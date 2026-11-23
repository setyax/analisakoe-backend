-- 1. Tabel Delivery Order (DO)
CREATE TABLE sal.delivery_order_hdr (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    doc_no VARCHAR(50) NOT NULL,
    doc_date DATE NOT NULL,
    so_id BIGINT NOT NULL REFERENCES sal.sales_order_hdr(id),
    status VARCHAR(16) NOT NULL DEFAULT 'SHIPPED',
    remarks TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (company_id, doc_no)
);

CREATE TABLE sal.delivery_order_dtl (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    header_id BIGINT NOT NULL REFERENCES sal.delivery_order_hdr(id) ON DELETE CASCADE,
    item_id BIGINT NOT NULL,
    uom_id BIGINT NOT NULL,
    qty NUMERIC(18,4) NOT NULL
);

-- 2. Tabel Sales Invoice (AR Invoice)
CREATE TABLE sal.sales_invoice_hdr (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    doc_no VARCHAR(50) NOT NULL,
    doc_date DATE NOT NULL,
    due_date DATE NOT NULL,
    do_id BIGINT NOT NULL REFERENCES sal.delivery_order_hdr(id),
    customer_id BIGINT NOT NULL REFERENCES md.customer(id),
    total_amount NUMERIC(18,4) NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'POSTED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (company_id, doc_no)
);

CREATE TABLE sal.sales_invoice_dtl (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    header_id BIGINT NOT NULL REFERENCES sal.sales_invoice_hdr(id) ON DELETE CASCADE,
    item_id BIGINT NOT NULL,
    qty NUMERIC(18,4) NOT NULL,
    price NUMERIC(18,4) NOT NULL,
    total NUMERIC(18,4) NOT NULL
);

-- 3. Permission
INSERT INTO core.permission (code, module, description) VALUES
    ('sal.do.read', 'sal', 'Lihat Delivery Order'),
    ('sal.do.write', 'sal', 'Buat Delivery Order'),
    ('sal.inv.read', 'sal', 'Lihat Sales Invoice'),
    ('sal.inv.write', 'sal', 'Buat Sales Invoice')
ON CONFLICT (code) DO NOTHING;

INSERT INTO core.role_permission (role_id, permission_id)
SELECT 1, id FROM core.permission WHERE code LIKE 'sal.do%' OR code LIKE 'sal.inv%' ON CONFLICT DO NOTHING;

-- 4. Menu
INSERT INTO core.menu (parent_id, code, name, path, icon, sort_order, is_header, required_permission) VALUES
    ((SELECT id FROM core.menu WHERE code = 'transactions'), 'sal.do', 'Delivery Orders', '/ui/sales/do', 'truck', 27, FALSE, 'sal.do.read'),
    ((SELECT id FROM core.menu WHERE code = 'transactions'), 'sal.inv', 'Sales Invoices', '/ui/sales/invoices', 'file-text', 28, FALSE, 'sal.inv.read')
ON CONFLICT (code) DO NOTHING;