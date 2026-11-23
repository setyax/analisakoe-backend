INSERT INTO core.tenant (code, name) VALUES ('DEMO', 'Demo Tenant');

INSERT INTO core.company (tenant_id, code, name, base_currency, timezone)
VALUES (1, 'MAIN', 'Main Company', 'IDR', 'Asia/Jakarta');

INSERT INTO core.role (tenant_id, code, name) VALUES
    (1, 'ADMIN',   'Administrator'),
    (1, 'MANAGER', 'Manager'),
    (1, 'STAFF',   'Staff'),
    (1, 'VIEWER',  'Viewer');

INSERT INTO core.permission (code, module, description) VALUES
    ('core.user.read',    'core', 'Lihat user'),
    ('core.user.write',   'core', 'Kelola user'),
    ('core.role.read',    'core', 'Lihat role'),
    ('core.role.write',   'core', 'Kelola role'),
    ('md.item.read',      'md',   'Lihat item'),
    ('md.item.write',     'md',   'Kelola item'),
    ('md.customer.read',  'md',   'Lihat customer'),
    ('md.customer.write', 'md',   'Kelola customer'),
    ('md.supplier.read',  'md',   'Lihat supplier'),
    ('md.supplier.write', 'md',   'Kelola supplier');

INSERT INTO core.role_permission (role_id, permission_id)
SELECT 1, id FROM core.permission;

INSERT INTO core.app_user (tenant_id, username, email, password_hash, full_name, must_change_pwd)
VALUES (1, 'admin', 'admin@analisakoe.local', 'PLACEHOLDER', 'Administrator', TRUE);

INSERT INTO core.user_role (user_id, role_id) VALUES (1, 1);
INSERT INTO core.user_company (user_id, company_id) VALUES (1, 1);

INSERT INTO md.currency (code, name, symbol, decimal_places) VALUES
    ('IDR', 'Indonesian Rupiah', 'Rp', 2),
    ('USD', 'US Dollar',         '$',  2),
    ('EUR', 'Euro',              'E',  2),
    ('SGD', 'Singapore Dollar',  'S$', 2);

INSERT INTO md.uom (tenant_id, code, name) VALUES
    (1, 'PCS', 'Piece'),
    (1, 'BOX', 'Box'),
    (1, 'KG',  'Kilogram'),
    (1, 'LTR', 'Liter'),
    (1, 'MTR', 'Meter');

INSERT INTO md.warehouse (tenant_id, company_id, code, name) VALUES
    (1, 1, 'MAIN', 'Main Warehouse');

INSERT INTO sys.doc_number_counter (company_id, doc_type, period, prefix, last_number) VALUES
    (1, 'ITEM',      '2026', 'ITM-', 0),
    (1, 'CUSTOMER',  '2026', 'CUS-', 0),
    (1, 'SUPPLIER',  '2026', 'SUP-', 0),
    (1, 'SO',        '2026', 'SO-',  0),
    (1, 'PO',        '2026', 'PO-',  0),
    (1, 'INV',       '2026', 'INV-', 0),
    (1, 'JOURNAL',   '2026', 'JRN-', 0);