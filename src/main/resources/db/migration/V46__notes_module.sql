-- 1. Tambah Akun COA untuk Penyesuaian
INSERT INTO fin.chart_of_account (tenant_id, company_id, code, name, account_type, is_header, status) VALUES
(1, 1, '4103', 'Pendapatan Lain-lain', 'REVENUE', FALSE, 'ACTIVE'),
(1, 1, '6198', 'Beban Lain-lain', 'EXPENSE', FALSE, 'ACTIVE')
ON CONFLICT DO NOTHING;

-- 2. Tabel Notes (Terpadu untuk AR/AP DN/CN)
CREATE TABLE fin.notes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    doc_no VARCHAR(50) NOT NULL,
    doc_date DATE NOT NULL,
    note_type VARCHAR(10) NOT NULL CHECK (note_type IN ('AR_DN', 'AR_CN', 'AP_DN', 'AP_CN')),
    partner_id BIGINT NOT NULL, -- Bisa Customer ID atau Supplier ID
    ref_invoice_id BIGINT,
    total_amount NUMERIC(18,4) NOT NULL,
    tax_amount NUMERIC(18,4) DEFAULT 0,
    remarks TEXT,
    status VARCHAR(16) DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ DEFAULT now()
);

-- 3. Menu
INSERT INTO core.menu (parent_id, code, name, path, icon, sort_order, is_header, required_permission) VALUES
((SELECT id FROM core.menu WHERE code = 'finance'), 'fin.notes', 'Debit / Credit Notes', '/ui/finance/notes', 'file-minus', 64.5, FALSE, 'fin.journal.read')
ON CONFLICT (code) DO NOTHING;