-- Permission
INSERT INTO core.permission (code, module, description) VALUES
('fin.journal.read', 'fin', 'Lihat Jurnal Umum'),
('fin.report.is', 'fin', 'Laporan Laba Rugi'),
('fin.report.bs', 'fin', 'Laporan Neraca')
ON CONFLICT (code) DO NOTHING;

INSERT INTO core.role_permission (role_id, permission_id)
SELECT 1, id FROM core.permission WHERE code LIKE 'fin.journal%' OR code LIKE 'fin.report%' ON CONFLICT DO NOTHING;

-- Menu
INSERT INTO core.menu (parent_id, code, name, path, icon, sort_order, is_header, required_permission) VALUES
((SELECT id FROM core.menu WHERE code = 'finance'), 'fin.journal', 'General Journal', '/ui/finance/journals', 'book-open', 67, FALSE, 'fin.journal.read'),
((SELECT id FROM core.menu WHERE code = 'finance'), 'fin.report.is', 'Income Statement', '/ui/finance/reports/income-statement', 'bar-chart-2', 68, FALSE, 'fin.report.is'),
((SELECT id FROM core.menu WHERE code = 'finance'), 'fin.report.bs', 'Balance Sheet', '/ui/finance/reports/balance-sheet', 'pie-chart', 69, FALSE, 'fin.report.bs')
ON CONFLICT (code) DO NOTHING;