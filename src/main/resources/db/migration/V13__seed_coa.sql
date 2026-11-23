DO $$
DECLARE
    v_asset BIGINT;
    v_liability BIGINT;
    v_equity BIGINT;
    v_revenue BIGINT;
    v_cogs BIGINT;
    v_opex BIGINT;
BEGIN
    -- ==========================================
    -- LEVEL 1: HEADER ACCOUNTS
    -- ==========================================
    INSERT INTO fin.chart_of_account (tenant_id, company_id, code, name, account_type, parent_id, is_header, status) 
    VALUES (1, 1, '1000', 'Aset', 'ASSET', NULL, TRUE, 'ACTIVE') RETURNING id INTO v_asset;
    
    INSERT INTO fin.chart_of_account (tenant_id, company_id, code, name, account_type, parent_id, is_header, status) 
    VALUES (1, 1, '2000', 'Kewajiban', 'LIABILITY', NULL, TRUE, 'ACTIVE') RETURNING id INTO v_liability;
    
    INSERT INTO fin.chart_of_account (tenant_id, company_id, code, name, account_type, parent_id, is_header, status) 
    VALUES (1, 1, '3000', 'Ekuitas', 'EQUITY', NULL, TRUE, 'ACTIVE') RETURNING id INTO v_equity;
    
    INSERT INTO fin.chart_of_account (tenant_id, company_id, code, name, account_type, parent_id, is_header, status) 
    VALUES (1, 1, '4000', 'Pendapatan', 'REVENUE', NULL, TRUE, 'ACTIVE') RETURNING id INTO v_revenue;
    
    INSERT INTO fin.chart_of_account (tenant_id, company_id, code, name, account_type, parent_id, is_header, status) 
    VALUES (1, 1, '5000', 'Harga Pokok Penjualan', 'EXPENSE', NULL, TRUE, 'ACTIVE') RETURNING id INTO v_cogs;
    
    INSERT INTO fin.chart_of_account (tenant_id, company_id, code, name, account_type, parent_id, is_header, status) 
    VALUES (1, 1, '6000', 'Beban Operasional', 'EXPENSE', NULL, TRUE, 'ACTIVE') RETURNING id INTO v_opex;

    -- ==========================================
    -- LEVEL 2: DETAIL ACCOUNTS (ASSET)
    -- ==========================================
    INSERT INTO fin.chart_of_account (tenant_id, company_id, code, name, account_type, parent_id, is_header, status) VALUES
    (1, 1, '1101', 'Kas Kecil', 'ASSET', v_asset, FALSE, 'ACTIVE'),
    (1, 1, '1102', 'Bank BCA', 'ASSET', v_asset, FALSE, 'ACTIVE'),
    (1, 1, '1201', 'Piutang Usaha', 'ASSET', v_asset, FALSE, 'ACTIVE'),
    (1, 1, '1301', 'Persediaan Barang Dagang', 'ASSET', v_asset, FALSE, 'ACTIVE'),
    (1, 1, '1401', 'Peralatan Kantor', 'ASSET', v_asset, FALSE, 'ACTIVE');

    -- ==========================================
    -- LEVEL 2: DETAIL ACCOUNTS (LIABILITY)
    -- ==========================================
    INSERT INTO fin.chart_of_account (tenant_id, company_id, code, name, account_type, parent_id, is_header, status) VALUES
    (1, 1, '2101', 'Hutang Usaha', 'LIABILITY', v_liability, FALSE, 'ACTIVE'),
    (1, 1, '2201', 'Hutang PPN', 'LIABILITY', v_liability, FALSE, 'ACTIVE'),
    (1, 1, '2202', 'Hutang PPh 21', 'LIABILITY', v_liability, FALSE, 'ACTIVE');

    -- ==========================================
    -- LEVEL 2: DETAIL ACCOUNTS (EQUITY)
    -- ==========================================
    INSERT INTO fin.chart_of_account (tenant_id, company_id, code, name, account_type, parent_id, is_header, status) VALUES
    (1, 1, '3101', 'Modal Disetor', 'EQUITY', v_equity, FALSE, 'ACTIVE'),
    (1, 1, '3201', 'Laba Ditahan', 'EQUITY', v_equity, FALSE, 'ACTIVE'),
    (1, 1, '3301', 'Laba Tahun Berjalan', 'EQUITY', v_equity, FALSE, 'ACTIVE');

    -- ==========================================
    -- LEVEL 2: DETAIL ACCOUNTS (REVENUE)
    -- ==========================================
    INSERT INTO fin.chart_of_account (tenant_id, company_id, code, name, account_type, parent_id, is_header, status) VALUES
    (1, 1, '4101', 'Pendapatan Penjualan', 'REVENUE', v_revenue, FALSE, 'ACTIVE'),
    (1, 1, '4201', 'Diskon Penjualan', 'REVENUE', v_revenue, FALSE, 'ACTIVE');

    -- ==========================================
    -- LEVEL 2: DETAIL ACCOUNTS (COGS)
    -- ==========================================
    INSERT INTO fin.chart_of_account (tenant_id, company_id, code, name, account_type, parent_id, is_header, status) VALUES
    (1, 1, '5101', 'Beban Pokok Penjualan', 'EXPENSE', v_cogs, FALSE, 'ACTIVE');

    -- ==========================================
    -- LEVEL 2: DETAIL ACCOUNTS (OPEX)
    -- ==========================================
    INSERT INTO fin.chart_of_account (tenant_id, company_id, code, name, account_type, parent_id, is_header, status) VALUES
    (1, 1, '6101', 'Beban Gaji Karyawan', 'EXPENSE', v_opex, FALSE, 'ACTIVE'),
    (1, 1, '6102', 'Beban Listrik dan Air', 'EXPENSE', v_opex, FALSE, 'ACTIVE'),
    (1, 1, '6103', 'Beban Sewa Kantor', 'EXPENSE', v_opex, FALSE, 'ACTIVE');
END $$;