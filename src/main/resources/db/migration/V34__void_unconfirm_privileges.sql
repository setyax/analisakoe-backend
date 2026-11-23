-- Permission
INSERT INTO core.permission (code, module, description) VALUES
('sal.so.void', 'sal', 'Batalkan Sales Order'),
('sal.so.unconfirm', 'sal', 'Batal Konfirmasi Sales Order'),
('sal.inv.void', 'sal', 'Batalkan Sales Invoice'),
('sal.do.void', 'sal', 'Batalkan Delivery Order')
ON CONFLICT (code) DO NOTHING;

-- Berikan ke ADMIN
INSERT INTO core.role_permission (role_id, permission_id)
SELECT 1, id FROM core.permission WHERE code LIKE 'sal.%void' OR code LIKE 'sal.so.unconfirm' ON CONFLICT DO NOTHING;