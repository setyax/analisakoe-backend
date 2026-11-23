-- Re-insert Menu Delivery Order dan Sales Invoice di bawah folder Sales
INSERT INTO core.menu (parent_id, code, name, path, icon, sort_order, is_header, required_permission) VALUES
((SELECT id FROM core.menu WHERE code = 'sales'), 'sal.do', 'Delivery Orders', '/ui/sales/do', 'truck', 32, FALSE, 'sal.do.read'),
((SELECT id FROM core.menu WHERE code = 'sales'), 'sal.inv', 'Sales Invoices', '/ui/sales/invoices', 'file-text', 33, FALSE, 'sal.inv.read')
ON CONFLICT (code) DO NOTHING;