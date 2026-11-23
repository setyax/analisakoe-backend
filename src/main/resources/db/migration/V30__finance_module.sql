-- 1. Master Bank Account
CREATE TABLE fin.bank_account (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    account_no VARCHAR(50),
    status VARCHAR(16) DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed Bank
INSERT INTO fin.bank_account (tenant_id, company_id, name, account_no) VALUES
(1, 1, 'Kas Kecil', 'KAS-001'),
(1, 1, 'Bank BCA', '1234567890'),
(1, 1, 'Bank Mandiri', '9876543210');

-- 2. Tabel Transaksi Kas & Bank (Umum)
CREATE TABLE fin.cash_bank_trx (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    doc_no VARCHAR(50) NOT NULL,
    doc_date DATE NOT NULL,
    trx_type VARCHAR(10) NOT NULL CHECK (trx_type IN ('IN', 'OUT')),
    bank_id BIGINT NOT NULL REFERENCES fin.bank_account(id),
    amount NUMERIC(18,4) NOT NULL,
    remarks TEXT,
    status VARCHAR(16) DEFAULT 'POSTED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 3. Tabel Pelunasan Piutang (AR Collection)
CREATE TABLE fin.ar_collection (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    doc_no VARCHAR(50) NOT NULL,
    doc_date DATE NOT NULL,
    customer_id BIGINT NOT NULL,
    bank_id BIGINT NOT NULL REFERENCES fin.bank_account(id),
    amount NUMERIC(18,4) NOT NULL,
    remarks TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 4. Tabel Pelunasan Hutang (AP Payment)
CREATE TABLE fin.ap_payment (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    doc_no VARCHAR(50) NOT NULL,
    doc_date DATE NOT NULL,
    supplier_id BIGINT NOT NULL,
    bank_id BIGINT NOT NULL REFERENCES fin.bank_account(id),
    amount NUMERIC(18,4) NOT NULL,
    remarks TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 5. Permission & Menu
INSERT INTO core.permission (code, module, description) VALUES
('fin.cashbank.read', 'fin', 'Lihat Kas & Bank'),
('fin.cashbank.write', 'fin', 'Kelola Kas & Bank'),
('fin.ar.read', 'fin', 'Lihat Pelunasan Piutang'),
('fin.ar.write', 'fin', 'Kelola Pelunasan Piutang'),
('fin.ap.read', 'fin', 'Lihat Pelunasan Hutang'),
('fin.ap.write', 'fin', 'Kelola Pelunasan Hutang')
ON CONFLICT (code) DO NOTHING;

INSERT INTO core.role_permission (role_id, permission_id)
SELECT 1, id FROM core.permission WHERE code LIKE 'fin.cashbank%' OR code LIKE 'fin.ar%' OR code LIKE 'fin.ap%' ON CONFLICT DO NOTHING;

INSERT INTO core.menu (parent_id, code, name, path, icon, sort_order, is_header, required_permission) VALUES
((SELECT id FROM core.menu WHERE code = 'finance'), 'fin.banks', 'Bank Accounts', '/ui/finance/banks', 'credit-card', 63, FALSE, 'fin.cashbank.read'),
((SELECT id FROM core.menu WHERE code = 'finance'), 'fin.cashbank', 'Cash & Bank', '/ui/finance/cashbank', 'dollar-sign', 64, FALSE, 'fin.cashbank.read'),
((SELECT id FROM core.menu WHERE code = 'finance'), 'fin.ar', 'AR Collections', '/ui/finance/ar', 'download', 65, FALSE, 'fin.ar.read'),
((SELECT id FROM core.menu WHERE code = 'finance'), 'fin.ap', 'AP Payments', '/ui/finance/ap', 'upload', 66, FALSE, 'fin.ap.read')
ON CONFLICT (code) DO NOTHING;