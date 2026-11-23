-- Tambah permission COA
INSERT INTO core.permission (code, module, description) VALUES
    ('fin.coa.read',  'fin', 'Lihat Chart of Accounts'),
    ('fin.coa.write', 'fin', 'Kelola Chart of Accounts')
ON CONFLICT (code) DO NOTHING;

-- Berikan permission COA ke role ADMIN
INSERT INTO core.role_permission (role_id, permission_id)
SELECT 1, id FROM core.permission WHERE code IN ('fin.coa.read', 'fin.coa.write')
ON CONFLICT DO NOTHING;

-- Tambah menu COA di bawah Master Data (parent_id = 2)
INSERT INTO core.menu (parent_id, code, name, path, icon, sort_order, is_header, required_permission) VALUES
    (2, 'fin.coa', 'Chart of Accounts', '/ui/coa', 'book', 26, FALSE, 'fin.coa.read')
ON CONFLICT (code) DO NOTHING;