-- 1. Tabel Header Sales Order
CREATE TABLE sal.sales_order_hdr (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL REFERENCES core.company(id),
    doc_no VARCHAR(50) NOT NULL,
    doc_date DATE NOT NULL,
    customer_id BIGINT NOT NULL REFERENCES md.customer(id),
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED')),
    remarks TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (company_id, doc_no)
);

-- 2. Tabel Detail Sales Order
CREATE TABLE sal.sales_order_dtl (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    header_id BIGINT NOT NULL REFERENCES sal.sales_order_hdr(id) ON DELETE CASCADE,
    item_id BIGINT NOT NULL REFERENCES md.item(id),
    uom_id BIGINT NOT NULL REFERENCES md.uom(id),
    qty NUMERIC(18,4) NOT NULL CHECK (qty > 0),
    price NUMERIC(18,4) NOT NULL DEFAULT 0
);

CREATE INDEX idx_so_hdr ON sal.sales_order_hdr(doc_date, status);
CREATE INDEX idx_so_dtl ON sal.sales_order_dtl(header_id);

-- 3. Permission Sales
INSERT INTO core.permission (code, module, description) VALUES
    ('sal.so.read', 'sal', 'Lihat Sales Order'),
    ('sal.so.write', 'sal', 'Kelola Sales Order')
ON CONFLICT (code) DO NOTHING;

INSERT INTO core.role_permission (role_id, permission_id)
SELECT 1, id FROM core.permission WHERE code LIKE 'sal.%' ON CONFLICT DO NOTHING;

-- 4. Menu Transactions
INSERT INTO core.menu (parent_id, code, name, path, icon, sort_order, is_header, required_permission) VALUES
    (NULL, 'transactions', 'Transactions', NULL, 'activity', 25, TRUE, NULL),
    ((SELECT id FROM core.menu WHERE code = 'transactions'), 'sal.so', 'Sales Orders', '/ui/sales/orders', 'shopping-cart', 26, FALSE, 'sal.so.read')
ON CONFLICT (code) DO NOTHING;