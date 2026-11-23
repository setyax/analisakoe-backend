INSERT INTO core.menu (parent_id, code, name, path, icon, sort_order, is_header, required_permission) VALUES
((SELECT id FROM core.menu WHERE code = 'finance'), 'fin.batch', 'Batch Posting', '/ui/finance/batch-posting', 'layers', 70, FALSE, 'fin.journal.read')
ON CONFLICT (code) DO NOTHING;