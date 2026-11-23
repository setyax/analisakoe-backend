-- 1. Tabel GRN Header
CREATE TABLE pur.grn_hdr (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL REFERENCES core.company(id),
    doc_no VARCHAR(50) NOT NULL,
    doc_date DATE NOT NULL,
    po_id BIGINT NOT NULL REFERENCES pur.purchase_order_hdr(id),
    remarks TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (company_id, doc_no)
);

-- 2. Tabel GRN Detail
CREATE TABLE pur.grn_dtl (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    header_id BIGINT NOT NULL REFERENCES pur.grn_hdr(id) ON DELETE CASCADE,
    item_id BIGINT NOT NULL REFERENCES md.item(id),
    uom_id BIGINT NOT NULL REFERENCES md.uom(id),
    qty_received NUMERIC(18,4) NOT NULL CHECK (qty_received > 0)
);

-- 3. Permission GRN
INSERT INTO core.permission (code, module, description) VALUES
    ('pur.grn.read', 'pur', 'Lihat Penerimaan Barang'),
    ('pur.grn.write', 'pur', 'Buat Penerimaan Barang')
ON CONFLICT (code) DO NOTHING;

INSERT INTO core.role_permission (role_id, permission_id)
SELECT 1, id FROM core.permission WHERE code LIKE 'pur.grn%' ON CONFLICT DO NOTHING;

-- 4. Menu GRN
INSERT INTO core.menu (parent_id, code, name, path, icon, sort_order, is_header, required_permission) VALUES
    ((SELECT id FROM core.menu WHERE code = 'purchasing'), 'pur.grn', 'Goods Receipts (GRN)', '/ui/purchasing/grn', 'inbox', 30, FALSE, 'pur.grn.read')
ON CONFLICT (code) DO NOTHING;