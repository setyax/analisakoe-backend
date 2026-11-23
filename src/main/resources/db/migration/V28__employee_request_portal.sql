-- 1. Hubungkan AppUser dengan Employee
ALTER TABLE core.app_user ADD COLUMN employee_id BIGINT REFERENCES hr.employee(id);

-- 2. Set admin sebagai Employee ID 1 (Budi Santoso) untuk testing
UPDATE core.app_user SET employee_id = 1 WHERE username = 'admin';

-- 3. Tambah Permission
INSERT INTO core.permission (code, module, description) VALUES
    ('hr.request.read', 'hr', 'Lihat Permintaan Sendiri'),
    ('hr.request.write', 'hr', 'Buat Permintaan Sendiri')
ON CONFLICT (code) DO NOTHING;

INSERT INTO core.role_permission (role_id, permission_id)
SELECT 1, id FROM core.permission WHERE code LIKE 'hr.request%' ON CONFLICT DO NOTHING;

-- 4. Tambah Menu My Requests di bawah HR & GA
INSERT INTO core.menu (parent_id, code, name, path, icon, sort_order, is_header, required_permission) VALUES
    ((SELECT id FROM core.menu WHERE code = 'hrga'), 'hr.myrequests', 'My Requests', '/ui/my-requests', 'file-text', 71, FALSE, 'hr.request.read')
ON CONFLICT (code) DO NOTHING;