-- Akun demo read-only untuk keperluan presentasi.
-- Password: demo123 (BCrypt). Flyway saat ini dinonaktifkan; jalankan manual via psql bila belum ada.

INSERT INTO core.role (tenant_id, code, name, description)
VALUES (1, 'DEMO', 'Demo (Read Only)', 'Akun demo: hanya melihat data, tanpa akses tulis')
ON CONFLICT (tenant_id, code) DO NOTHING;

INSERT INTO core.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM core.role r CROSS JOIN core.permission p
WHERE r.tenant_id = 1 AND r.code = 'DEMO'
  AND (p.code LIKE '%.read' OR p.code IN ('fin.report.bs', 'fin.report.is'))
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO core.app_user (tenant_id, username, password_hash, full_name, status, must_change_pwd, default_company_id)
VALUES (1, 'demo', '$2a$10$0LiNjHXg2Y9JUTXUdO0v0uhOGe5yHQ6jCmfb6lBiGv1l.T6ZArPMe', 'Akun Demo', 'ACTIVE', false, 11)
ON CONFLICT (tenant_id, username) DO NOTHING;

INSERT INTO core.user_company (user_id, company_id)
SELECT id, 11 FROM core.app_user WHERE username = 'demo'
ON CONFLICT (user_id, company_id) DO NOTHING;

INSERT INTO core.user_role (user_id, role_id)
SELECT u.id, r.id FROM core.app_user u CROSS JOIN core.role r
WHERE u.username = 'demo' AND r.tenant_id = 1 AND r.code = 'DEMO'
  AND NOT EXISTS (SELECT 1 FROM core.user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

-- Menu UI: izinkan modul untuk dilihat, tanpa USER_MGMT.
INSERT INTO sys.user_permission (user_id, permission_code)
SELECT u.id, m.code FROM core.app_user u CROSS JOIN
    (VALUES ('DASHBOARD'), ('PURCHASING'), ('SALES'), ('INVENTORY'),
            ('FINANCE'), ('MASTER_DATA'), ('HRGA')) AS m(code)
WHERE u.username = 'demo'
ON CONFLICT (user_id, permission_code) DO NOTHING;
