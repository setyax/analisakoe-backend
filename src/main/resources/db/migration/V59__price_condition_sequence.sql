-- SAP-style condition records + price determination sequence.
-- condition_source : CONTRACT -> INFO_RECORD -> RFQ -> LAST_PO -> MANUAL
-- condition_class  : PRICE | DISCOUNT | SURCHARGE | TAX
-- rate             : persentase untuk DISCOUNT/SURCHARGE/TAX
-- calculation_order: urutan hitung (>10 setelah harga dasar, 90 untuk pajak)

ALTER TABLE pur.price_list
    ADD COLUMN IF NOT EXISTS condition_source VARCHAR(20) NOT NULL DEFAULT 'INFO_RECORD',
    ADD COLUMN IF NOT EXISTS condition_class VARCHAR(16) NOT NULL DEFAULT 'PRICE',
    ADD COLUMN IF NOT EXISTS rate NUMERIC(9,4),
    ADD COLUMN IF NOT EXISTS calculation_order INTEGER DEFAULT 10;

UPDATE pur.price_list
   SET condition_source = 'INFO_RECORD',
       condition_class = 'PRICE',
       calculation_order = 10
 WHERE condition_class IS NULL OR condition_class = '';

-- Kunci unik lama diganti supaya kondisi diskon/pajak tidak bentrok dengan PB00.
DROP INDEX IF EXISTS pur.uq_price_list_condition;
CREATE UNIQUE INDEX uq_price_list_condition
    ON pur.price_list (
        tenant_id, company_id, supplier_id, item_id,
        COALESCE(uom_id, 0), COALESCE(currency, 'IDR'),
        valid_from, COALESCE(valid_to, DATE '9999-12-31'),
        COALESCE(min_qty, 1), condition_source,
        condition_class, condition_type
    );

CREATE INDEX IF NOT EXISTS idx_price_list_conditions
    ON pur.price_list (tenant_id, company_id, supplier_id, item_id, uom_id,
                       condition_class, valid_from, valid_to);

-- Jejak kondisi yang benar-benar dipakai tiap baris PO (audit / SAP condition tab).
CREATE TABLE IF NOT EXISTS pur.po_condition (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id         BIGINT NOT NULL,
    company_id        BIGINT NOT NULL,
    header_id         BIGINT NOT NULL REFERENCES pur.purchase_order_hdr(id) ON DELETE CASCADE,
    dtl_id            BIGINT NOT NULL REFERENCES pur.purchase_order_dtl(id) ON DELETE CASCADE,
    condition_source  VARCHAR(20) NOT NULL,
    condition_class   VARCHAR(16) NOT NULL,
    condition_type    VARCHAR(10) NOT NULL,
    rate              NUMERIC(9,4),
    amount            NUMERIC(19,4),
    calculation_order INTEGER NOT NULL DEFAULT 10,
    created_at        TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_po_condition_dtl
    ON pur.po_condition (tenant_id, company_id, dtl_id, calculation_order);
