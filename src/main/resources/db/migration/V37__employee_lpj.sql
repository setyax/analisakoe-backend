-- 1. Tambah Menu Employees (Karyawan) di HR & GA
INSERT INTO core.menu (parent_id, code, name, path, icon, sort_order, is_header, required_permission) VALUES
((SELECT id FROM core.menu WHERE code = 'hrga'), 'hr.employees', 'Employees', '/ui/hr/employees', 'users', 71, FALSE, 'hr.request.read')
ON CONFLICT (code) DO NOTHING;

-- 2. Tambah kolom untuk LPJ di tabel Kas Bon
ALTER TABLE hr.cash_advance ADD COLUMN expense_amount NUMERIC(18,4) DEFAULT 0;
ALTER TABLE hr.cash_advance ADD COLUMN return_amount NUMERIC(18,4) DEFAULT 0;
ALTER TABLE hr.cash_advance ADD COLUMN settlement_date DATE;