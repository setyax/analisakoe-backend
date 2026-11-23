-- 1. Tambah Akun COA untuk Retur
INSERT INTO fin.chart_of_account (tenant_id, company_id, code, name, account_type, is_header, status) VALUES
(1, 1, '4102', 'Retur Penjualan', 'REVENUE', FALSE, 'ACTIVE'), -- Kontra Pendapatan
(1, 1, '5102', 'Retur Pembelian', 'EXPENSE', FALSE, 'ACTIVE')  -- Kontra Beban/HPP
ON CONFLICT DO NOTHING;

-- 2. Tabel Retur Penjualan (Sales Return)
CREATE TABLE sal.sales_return_hdr (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    doc_no VARCHAR(50) NOT NULL,
    doc_date DATE NOT NULL,
    customer_id BIGINT NOT NULL REFERENCES md.customer(id),
    sales_invoice_id BIGINT REFERENCES sal.sales_invoice_hdr(id),
    total_amount NUMERIC(18,4) NOT NULL,
    status VARCHAR(16) DEFAULT 'DRAFT',
    remarks TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sal.sales_return_dtl (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    header_id BIGINT NOT NULL REFERENCES sal.sales_return_hdr(id) ON DELETE CASCADE,
    item_id BIGINT NOT NULL REFERENCES md.item(id),
    uom_id BIGINT NOT NULL REFERENCES md.uom(id),
    qty NUMERIC(18,4) NOT NULL,
    price NUMERIC(18,4) NOT NULL,
    total NUMERIC(18,4) NOT NULL
);

-- 3. Tabel Retur Pembelian (Purchase Return)
CREATE TABLE pur.purchase_return_hdr (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    doc_no VARCHAR(50) NOT NULL,
    doc_date DATE NOT NULL,
    supplier_id BIGINT NOT NULL REFERENCES md.supplier(id),
    purchase_invoice_id BIGINT REFERENCES pur.purchase_invoice_hdr(id),
    total_amount NUMERIC(18,4) NOT NULL,
    status VARCHAR(16) DEFAULT 'DRAFT',
    remarks TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE pur.purchase_return_dtl (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    header_id BIGINT NOT NULL REFERENCES pur.purchase_return_hdr(id) ON DELETE CASCADE,
    item_id BIGINT NOT NULL REFERENCES md.item(id),
    uom_id BIGINT NOT NULL REFERENCES md.uom(id),
    qty NUMERIC(18,4) NOT NULL,
    price NUMERIC(18,4) NOT NULL,
    total NUMERIC(18,4) NOT NULL
);

-- 4. Permission & Menu
INSERT INTO core.permission (code, module, description) VALUES
('sal.return.read', 'sal', 'Lihat Retur Penjualan'),
('sal.return.write', 'sal', 'Buat Retur Penjualan'),
('pur.return.read', 'pur', 'Lihat Retur Pembelian'),
('pur.return.write', 'pur', 'Buat Retur Pembelian')
ON CONFLICT (code) DO NOTHING;

INSERT INTO core.role_permission (role_id, permission_id)
SELECT 1, id FROM core.permission WHERE code LIKE 'sal.return%' OR code LIKE 'pur.return%' ON CONFLICT DO NOTHING;

INSERT INTO core.menu (parent_id, code, name, path, icon, sort_order, is_header, required_permission) VALUES
((SELECT id FROM core.menu WHERE code = 'sales'), 'sal.return', 'Sales Returns', '/ui/sales/returns', 'corner-up-left', 34, FALSE, 'sal.return.read'),
((SELECT id FROM core.menu WHERE code = 'purchasing'), 'pur.return', 'Purchase Returns', '/ui/purchasing/returns', 'corner-up-right', 45, FALSE, 'pur.return.read')
ON CONFLICT (code) DO NOTHING;