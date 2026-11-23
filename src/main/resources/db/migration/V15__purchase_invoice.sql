-- 1. Tabel Purchase Invoice Header
CREATE TABLE pur.purchase_invoice_hdr (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL REFERENCES core.company(id),
    doc_no VARCHAR(50) NOT NULL, -- Nomor Invoice Internal
    supplier_invoice_no VARCHAR(50) NOT NULL, -- Nomor Faktur Pajak/Supplier
    invoice_date DATE NOT NULL,
    due_date DATE NOT NULL,
    supplier_id BIGINT NOT NULL REFERENCES md.supplier(id),
    grn_id BIGINT REFERENCES pur.grn_hdr(id),
    total_amount NUMERIC(18,4) NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'POSTED', 'PAID')),
    remarks TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (company_id, doc_no)
);

-- 2. Tabel Purchase Invoice Detail
CREATE TABLE pur.purchase_invoice_dtl (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    header_id BIGINT NOT NULL REFERENCES pur.purchase_invoice_hdr(id) ON DELETE CASCADE,
    item_id BIGINT NOT NULL REFERENCES md.item(id),
    qty NUMERIC(18,4) NOT NULL CHECK (qty > 0),
    price NUMERIC(18,4) NOT NULL DEFAULT 0,
    total NUMERIC(18,4) NOT NULL DEFAULT 0
);

-- 3. Permission
INSERT INTO core.permission (code, module, description) VALUES
    ('pur.inv.read', 'pur', 'Lihat Purchase Invoice'),
    ('pur.inv.write', 'pur', 'Kelola Purchase Invoice')
ON CONFLICT (code) DO NOTHING;

INSERT INTO core.role_permission (role_id, permission_id)
SELECT 1, id FROM core.permission WHERE code LIKE 'pur.inv%' ON CONFLICT DO NOTHING;

-- 4. Menu
INSERT INTO core.menu (parent_id, code, name, path, icon, sort_order, is_header, required_permission) VALUES
    ((SELECT id FROM core.menu WHERE code = 'purchasing'), 'pur.inv', 'Purchase Invoices', '/ui/purchasing/invoices', 'file-text', 33, FALSE, 'pur.inv.read')
ON CONFLICT (code) DO NOTHING;