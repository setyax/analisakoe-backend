-- 1. Tabel Header Transaksi (Jurnal Mutasi)
CREATE TABLE inv.transaction_header (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL REFERENCES core.company(id),
    doc_type VARCHAR(10) NOT NULL CHECK (doc_type IN ('IN', 'OUT', 'ADJ')),
    doc_no VARCHAR(50) NOT NULL,
    doc_date DATE NOT NULL,
    warehouse_id BIGINT NOT NULL REFERENCES md.warehouse(id),
    remarks TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'POSTED' CHECK (status IN ('DRAFT', 'POSTED', 'VOID')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (company_id, doc_no)
);

-- 2. Tabel Detail Transaksi (Item yang masuk/keluar)
CREATE TABLE inv.transaction_detail (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    header_id BIGINT NOT NULL REFERENCES inv.transaction_header(id) ON DELETE CASCADE,
    item_id BIGINT NOT NULL REFERENCES md.item(id),
    uom_id BIGINT NOT NULL REFERENCES md.uom(id),
    qty NUMERIC(18,4) NOT NULL CHECK (qty > 0)
);

CREATE INDEX idx_inv_trx_hdr ON inv.transaction_header(doc_date, doc_type);
CREATE INDEX idx_inv_trx_dtl ON inv.transaction_detail(header_id);

-- 3. Tabel Saldo Stok (Stock Balance)
CREATE TABLE inv.stock_balance (
    warehouse_id BIGINT NOT NULL REFERENCES md.warehouse(id),
    item_id BIGINT NOT NULL REFERENCES md.item(id),
    qty_on_hand NUMERIC(18,4) NOT NULL DEFAULT 0,
    last_updated TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (warehouse_id, item_id)
);

-- 4. Tambah Permission
INSERT INTO core.permission (code, module, description) VALUES
    ('inv.trx.read', 'inv', 'Lihat Transaksi Inventory'),
    ('inv.trx.write', 'inv', 'Buat Transaksi Inventory'),
    ('inv.balance.read', 'inv', 'Lihat Saldo Stok')
ON CONFLICT (code) DO NOTHING;

-- 5. Berikan ke ADMIN
INSERT INTO core.role_permission (role_id, permission_id)
SELECT 1, id FROM core.permission WHERE code LIKE 'inv.%'
ON CONFLICT DO NOTHING;

-- 6. Tambah Menu Sidebar
INSERT INTO core.menu (parent_id, code, name, path, icon, sort_order, is_header, required_permission) VALUES
    (NULL, 'inventory', 'Inventory', NULL, 'box', 30, TRUE, NULL),
    ((SELECT id FROM core.menu WHERE code = 'inventory'), 'inv.balance', 'Stock Balance', '/ui/inventory/balance', 'layers', 31, FALSE, 'inv.balance.read'),
    ((SELECT id FROM core.menu WHERE code = 'inventory'), 'inv.trx', 'Transactions', '/ui/inventory/transactions', 'activity', 32, FALSE, 'inv.trx.read')
ON CONFLICT (code) DO NOTHING;