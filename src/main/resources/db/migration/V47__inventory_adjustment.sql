-- Tambah Akun COA untuk Selisih Persediaan
INSERT INTO fin.chart_of_account (tenant_id, company_id, code, name, account_type, is_header, status) VALUES
(1, 1, '8103', 'Pendapatan Selisih Persediaan', 'REVENUE', FALSE, 'ACTIVE'),
(1, 1, '6197', 'Beban Selisih Persediaan', 'EXPENSE', FALSE, 'ACTIVE')
ON CONFLICT DO NOTHING;

-- Menu Inventory Adjustment
INSERT INTO core.menu (parent_id, code, name, path, icon, sort_order, is_header, required_permission) VALUES
((SELECT id FROM core.menu WHERE code = 'inventory'), 'inv.adjustment', 'Inventory Adjustment', '/ui/inventory/adjustment', 'sliders', 53, FALSE, 'inv.trx.write')
ON CONFLICT (code) DO NOTHING;