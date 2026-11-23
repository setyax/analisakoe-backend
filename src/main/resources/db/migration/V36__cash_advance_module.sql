-- 1. Tambah Akun COA untuk Piutang Karyawan dan Beban Perjalanan
INSERT INTO fin.chart_of_account (tenant_id, company_id, code, name, account_type, parent_id, is_header, status) VALUES
(1, 1, '1103', 'Piutang Karyawan', 'ASSET', (SELECT id FROM fin.chart_of_account WHERE code = '1000' LIMIT 1), FALSE, 'ACTIVE'),
(1, 1, '6104', 'Beban Perjalanan Dinas', 'EXPENSE', (SELECT id FROM fin.chart_of_account WHERE code = '6000' LIMIT 1), FALSE, 'ACTIVE')
ON CONFLICT DO NOTHING;

-- 2. Tabel Kas Bon (Header)
CREATE TABLE hr.cash_advance (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    doc_no VARCHAR(50) NOT NULL,
    doc_date DATE NOT NULL,
    employee_id BIGINT NOT NULL REFERENCES hr.employee(id),
    bank_id BIGINT NOT NULL REFERENCES fin.bank_account(id),
    amount NUMERIC(18,4) NOT NULL,
    remarks TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'POSTED', 'SETTLED', 'VOID')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 3. Permission
INSERT INTO core.permission (code, module, description) VALUES
('hr.cashadvance.read', 'hr', 'Lihat Kas Bon'),
('hr.cashadvance.write', 'hr', 'Buat Kas Bon'),
('hr.cashadvance.post', 'hr', 'Posting Kas Bon')
ON CONFLICT (code) DO NOTHING;

INSERT INTO core.role_permission (role_id, permission_id)
SELECT 1, id FROM core.permission WHERE code LIKE 'hr.cashadvance%' ON CONFLICT DO NOTHING;

-- 4. Menu
INSERT INTO core.menu (parent_id, code, name, path, icon, sort_order, is_header, required_permission) VALUES
((SELECT id FROM core.menu WHERE code = 'hrga'), 'hr.cashadvance', 'Cash Advances', '/ui/hr/cash-advance', 'credit-card', 72, FALSE, 'hr.cashadvance.read')
ON CONFLICT (code) DO NOTHING;