-- 1. Hapus header "Transactions" yang lama (jika ada)
DELETE FROM core.menu WHERE code = 'transactions';

-- 2. Buat Header Menu Baru (Jika Belum Ada)
INSERT INTO core.menu (code, name, path, icon, sort_order, is_header) VALUES
('sales', 'Sales', NULL, 'shopping-cart', 30, TRUE),
('purchasing', 'Purchasing', NULL, 'shopping-bag', 40, TRUE),
('inventory', 'Inventory', NULL, 'box', 50, TRUE),
('finance', 'Finance & Accounting', NULL, 'dollar-sign', 60, TRUE),
('hrga', 'HR & GA', NULL, 'users', 70, TRUE)
ON CONFLICT (code) DO UPDATE SET is_header = TRUE;

-- 3. Update parent_id untuk Sales
UPDATE core.menu SET parent_id = (SELECT id FROM core.menu WHERE code = 'sales'), sort_order = 31 WHERE code = 'sal.so';
UPDATE core.menu SET parent_id = (SELECT id FROM core.menu WHERE code = 'sales'), sort_order = 32 WHERE code = 'sal.do';
UPDATE core.menu SET parent_id = (SELECT id FROM core.menu WHERE code = 'sales'), sort_order = 33 WHERE code = 'sal.inv';

-- 4. Update parent_id untuk Purchasing
UPDATE core.menu SET parent_id = (SELECT id FROM core.menu WHERE code = 'purchasing'), sort_order = 41 WHERE code = 'pur.pr';
UPDATE core.menu SET parent_id = (SELECT id FROM core.menu WHERE code = 'purchasing'), sort_order = 42 WHERE code = 'pur.po';
UPDATE core.menu SET parent_id = (SELECT id FROM core.menu WHERE code = 'purchasing'), sort_order = 43 WHERE code = 'pur.grn';
UPDATE core.menu SET parent_id = (SELECT id FROM core.menu WHERE code = 'purchasing'), sort_order = 44 WHERE code = 'pur.inv';

-- 5. Update parent_id untuk Inventory
UPDATE core.menu SET parent_id = (SELECT id FROM core.menu WHERE code = 'inventory'), sort_order = 51 WHERE code = 'inv.balance';
UPDATE core.menu SET parent_id = (SELECT id FROM core.menu WHERE code = 'inventory'), sort_order = 52 WHERE code = 'inv.trx';

-- 6. Pindahkan COA dan Exchange Rate ke Finance
UPDATE core.menu SET parent_id = (SELECT id FROM core.menu WHERE code = 'finance'), sort_order = 61 WHERE code = 'fin.coa';
UPDATE core.menu SET parent_id = (SELECT id FROM core.menu WHERE code = 'finance'), sort_order = 62 WHERE code = 'md.xrates';

-- 7. Rapikan urutan Master Data
UPDATE core.menu SET sort_order = 21 WHERE code = 'md.items';
UPDATE core.menu SET sort_order = 22 WHERE code = 'md.customers';
UPDATE core.menu SET sort_order = 23 WHERE code = 'md.suppliers';
UPDATE core.menu SET sort_order = 24 WHERE code = 'md.uoms';
UPDATE core.menu SET sort_order = 25 WHERE code = 'md.warehouses';

-- 8. Rapikan urutan Administration
UPDATE core.menu SET sort_order = 81 WHERE code = 'core.users';
UPDATE core.menu SET sort_order = 82 WHERE code = 'core.roles';
UPDATE core.menu SET sort_order = 83 WHERE code = 'core.permissions';