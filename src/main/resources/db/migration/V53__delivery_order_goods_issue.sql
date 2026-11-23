-- AnalisaKoe Delivery Order / Goods Issue migration (version allocated at install): Delivery Order / Goods Issue lifecycle and audit trail.
-- Existing historical DO status is preserved. Legacy SHIPPED rows are flagged for review.

ALTER TABLE sal.delivery_order_hdr
    ADD COLUMN IF NOT EXISTS warehouse_id BIGINT,
    ADD COLUMN IF NOT EXISTS picking_status VARCHAR(16) NOT NULL DEFAULT 'NOT_STARTED',
    ADD COLUMN IF NOT EXISTS goods_issue_status VARCHAR(20) NOT NULL DEFAULT 'NOT_POSTED',
    ADD COLUMN IF NOT EXISTS pod_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS delivered_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS pod_reference VARCHAR(100),
    ADD COLUMN IF NOT EXISTS pod_remarks TEXT;

ALTER TABLE sal.delivery_order_hdr
    DROP CONSTRAINT IF EXISTS delivery_order_hdr_status_check;
ALTER TABLE sal.delivery_order_hdr ALTER COLUMN status TYPE VARCHAR(24);
ALTER TABLE sal.delivery_order_hdr ALTER COLUMN status SET DEFAULT 'DRAFT';

-- Default each existing DO to the first active warehouse belonging to its company.
UPDATE sal.delivery_order_hdr h
SET warehouse_id = (
    SELECT w.id
    FROM md.warehouse w
    WHERE w.tenant_id = h.tenant_id
      AND w.company_id = h.company_id
      AND w.status = 'ACTIVE'
    ORDER BY w.id
    LIMIT 1
)
WHERE h.warehouse_id IS NULL;

-- The two legacy SHIPPED rows found by the read-only audit have no matching legacy OUT.
-- Do not post them automatically: require a human stock/POD reconciliation first.
UPDATE sal.delivery_order_hdr
SET goods_issue_status = 'LEGACY_REVIEW',
    picking_status = 'READY'
WHERE status = 'SHIPPED'
  AND goods_issue_status = 'NOT_POSTED';

ALTER TABLE sal.delivery_order_hdr
    ADD CONSTRAINT delivery_order_hdr_status_check
    CHECK (status IN (
        'DRAFT','OPEN','IN_PICKING','READY_TO_SHIP','PARTIALLY_SHIPPED',
        'SHIPPED','DELIVERED','CLOSED','CANCELLED',
        'CONFIRMED','VOID'
    ));

ALTER TABLE sal.delivery_order_hdr
    ADD CONSTRAINT fk_sal_do_warehouse_do_gi
    FOREIGN KEY (warehouse_id) REFERENCES md.warehouse(id);

ALTER TABLE sal.delivery_order_hdr
    ADD CONSTRAINT ck_sal_do_picking_status_do_gi
    CHECK (picking_status IN ('NOT_STARTED','IN_PROGRESS','READY'));
ALTER TABLE sal.delivery_order_hdr
    ADD CONSTRAINT ck_sal_do_goods_issue_status_do_gi
    CHECK (goods_issue_status IN ('NOT_POSTED','PARTIAL','POSTED','REVERSED','LEGACY_REVIEW'));
ALTER TABLE sal.delivery_order_hdr
    ADD CONSTRAINT ck_sal_do_pod_status_do_gi
    CHECK (pod_status IN ('PENDING','DELIVERED','FAILED'));

ALTER TABLE sal.delivery_order_dtl
    ADD COLUMN IF NOT EXISTS gi_posted_qty NUMERIC(18,4) NOT NULL DEFAULT 0;
ALTER TABLE sal.delivery_order_dtl
    ADD CONSTRAINT ck_sal_do_dtl_gi_posted_qty_do_gi
    CHECK (gi_posted_qty >= 0 AND gi_posted_qty <= qty);

ALTER TABLE inv.transaction_header
    ADD COLUMN IF NOT EXISTS source_do_id BIGINT,
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(80),
    ADD COLUMN IF NOT EXISTS reversal_of_header_id BIGINT;

ALTER TABLE inv.transaction_detail
    ADD COLUMN IF NOT EXISTS source_do_detail_id BIGINT,
    ADD COLUMN IF NOT EXISTS reversal_of_detail_id BIGINT,
    ADD COLUMN IF NOT EXISTS stock_movement_id BIGINT;

ALTER TABLE inv.transaction_header
    ADD CONSTRAINT fk_inv_trx_source_do_do_gi
    FOREIGN KEY (source_do_id) REFERENCES sal.delivery_order_hdr(id);
ALTER TABLE inv.transaction_header
    ADD CONSTRAINT fk_inv_trx_reversal_header_do_gi
    FOREIGN KEY (reversal_of_header_id) REFERENCES inv.transaction_header(id);
ALTER TABLE inv.transaction_detail
    ADD CONSTRAINT fk_inv_trx_source_do_detail_do_gi
    FOREIGN KEY (source_do_detail_id) REFERENCES sal.delivery_order_dtl(id);
ALTER TABLE inv.transaction_detail
    ADD CONSTRAINT fk_inv_trx_reversal_detail_do_gi
    FOREIGN KEY (reversal_of_detail_id) REFERENCES inv.transaction_detail(id);

CREATE INDEX IF NOT EXISTS idx_sal_do_warehouse_status_do_gi
    ON sal.delivery_order_hdr(company_id, warehouse_id, status);
CREATE INDEX IF NOT EXISTS idx_inv_trx_source_do_do_gi
    ON inv.transaction_header(source_do_id, doc_type, id);
CREATE INDEX IF NOT EXISTS idx_inv_trx_detail_do_line_do_gi
    ON inv.transaction_detail(source_do_detail_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_inv_gi_idempotency_do_gi
    ON inv.transaction_header(company_id, source_do_id, idempotency_key)
    WHERE source_do_id IS NOT NULL AND idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_inv_gi_reversal_header_do_gi
    ON inv.transaction_header(reversal_of_header_id)
    WHERE reversal_of_header_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_inv_gi_reversal_detail_do_gi
    ON inv.transaction_detail(reversal_of_detail_id)
    WHERE reversal_of_detail_id IS NOT NULL;

-- The audit found no existing inventory period-lock table. Create an explicit control table.
-- A period must have an OPEN row before GI/reversal can be posted; CLOSED periods are rejected.
CREATE TABLE IF NOT EXISTS inv.inventory_period_control (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES core.tenant(id),
    company_id BIGINT NOT NULL REFERENCES core.company(id),
    period VARCHAR(7) NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CLOSED')),
    opened_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at TIMESTAMPTZ,
    closed_by VARCHAR(100),
    remarks TEXT,
    CONSTRAINT uq_inv_inventory_period_do_gi UNIQUE(company_id, period)
);
CREATE INDEX IF NOT EXISTS idx_inv_inventory_period_status_do_gi
    ON inv.inventory_period_control(company_id, status, period);

-- Open periods for existing DO dates and the current month. Other months require an explicit OPEN row.
INSERT INTO inv.inventory_period_control(tenant_id, company_id, period, status)
SELECT DISTINCT h.tenant_id, h.company_id, TO_CHAR(h.doc_date, 'YYYY-MM'), 'OPEN'
FROM sal.delivery_order_hdr h
WHERE h.company_id IS NOT NULL
UNION
SELECT c.tenant_id, c.id, TO_CHAR(CURRENT_DATE, 'YYYY-MM'), 'OPEN'
FROM core.company c
WHERE c.status = 'ACTIVE'
ON CONFLICT (company_id, period) DO NOTHING;
