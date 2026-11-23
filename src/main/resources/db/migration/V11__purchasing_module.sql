-- 1. Tabel Purchase Request (Header & Detail)
CREATE TABLE pur.purchase_request_hdr (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL REFERENCES core.company(id),
    doc_no VARCHAR(50) NOT NULL,
    doc_date DATE NOT NULL,
    remarks TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'APPROVED', 'PO_CREATED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (company_id, doc_no)
);

CREATE TABLE pur.purchase_request_dtl (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    header_id BIGINT NOT NULL REFERENCES pur.purchase_request_hdr(id) ON DELETE CASCADE,
    item_id BIGINT NOT NULL REFERENCES md.item(id),
    uom_id BIGINT NOT NULL REFERENCES md.uom(id),
    qty NUMERIC(18,4) NOT NULL CHECK (qty > 0)
);

-- 2. Tabel Purchase Order (Header & Detail)
CREATE TABLE pur.purchase_order_hdr (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL REFERENCES core.company(id),
    doc_no VARCHAR(50) NOT NULL,
    doc_date DATE NOT NULL,
    supplier_id BIGINT NOT NULL REFERENCES md.supplier(id),
    pr_id BIGINT REFERENCES pur.purchase_request_hdr(id), -- Opsional, jika PO dibuat dari PR
    remarks TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'CONFIRMED', 'RECEIVED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (company_id, doc_no)
);

CREATE TABLE pur.purchase_order_dtl (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    header_id BIGINT NOT NULL REFERENCES pur.purchase_order_hdr(id) ON DELETE CASCADE,
    item_id BIGINT NOT NULL REFERENCES md.item(id),
    uom_id BIGINT NOT NULL REFERENCES md.uom(id),
    qty NUMERIC(18,4) NOT NULL CHECK (qty > 0),
    price NUMERIC(18,4) NOT NULL DEFAULT 0
);

-- 3. Permission
INSERT INTO core.permission (code, module, description) VALUES
    ('pur.pr.read', 'pur', 'Lihat Purchase Request'),
    ('pur.pr.write', 'pur', 'Kelola Purchase Request'),
    ('pur.po.read', 'pur', 'Lihat Purchase Order'),
    ('pur.po.write', 'pur', 'Kelola Purchase Order')
ON CONFLICT (code) DO NOTHING;

INSERT INTO core.role_permission (role_id, permission_id)
SELECT 1, id FROM core.permission WHERE code LIKE 'pur.%' ON CONFLICT DO NOTHING;

-- 4. Menu Purchasing
INSERT INTO core.menu (parent_id, code, name, path, icon, sort_order, is_header, required_permission) VALUES
    (NULL, 'purchasing', 'Purchasing', NULL, 'shopping-bag', 27, TRUE, NULL),
    ((SELECT id FROM core.menu WHERE code = 'purchasing'), 'pur.pr', 'Purchase Requests', '/ui/purchasing/pr', 'file-text', 28, FALSE, 'pur.pr.read'),
    ((SELECT id FROM core.menu WHERE code = 'purchasing'), 'pur.po', 'Purchase Orders', '/ui/purchasing/po', 'file-plus', 29, FALSE, 'pur.po.read')
ON CONFLICT (code) DO NOTHING;