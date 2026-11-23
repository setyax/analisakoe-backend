-- 1. Tambah Akun COA untuk Aset & Depresiasi
INSERT INTO fin.chart_of_account (tenant_id, company_id, code, name, account_type, is_header, status) VALUES
(1, 1, '1401', 'Aset Tetap - Tanah', 'ASSET', FALSE, 'ACTIVE'),
(1, 1, '1402', 'Aset Tetap - Bangunan', 'ASSET', FALSE, 'ACTIVE'),
(1, 1, '1403', 'Aset Tetap - Kendaraan', 'ASSET', FALSE, 'ACTIVE'),
(1, 1, '1404', 'Aset Tetap - Peralatan & Mesin', 'ASSET', FALSE, 'ACTIVE'),
(1, 1, '1405', 'Aset Tetap - Perabotan Kantor', 'ASSET', FALSE, 'ACTIVE'),
(1, 1, '1491', 'Akumulasi Depresiasi Aset', 'ASSET', FALSE, 'ACTIVE'), -- Kontra Aset
(1, 1, '6199', 'Beban Depresiasi', 'EXPENSE', FALSE, 'ACTIVE')
ON CONFLICT DO NOTHING;

-- 2. Tabel Master Aset Tetap
CREATE TABLE fin.asset_master (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    asset_code VARCHAR(50) NOT NULL,
    asset_name VARCHAR(200) NOT NULL,
    category VARCHAR(50) NOT NULL, -- Tanah, Kendaraan, Peralatan
    acquisition_date DATE NOT NULL,
    acquisition_cost NUMERIC(18,4) NOT NULL, -- Harga Beli
    useful_life_years INT NOT NULL, -- Umur Ekonomis (Tahun)
    salvage_value NUMERIC(18,4) DEFAULT 0, -- Nilai Residu
    depreciation_method VARCHAR(20) DEFAULT 'STRAIGHT_LINE', -- Garis Lurus
    accumulated_depreciation NUMERIC(18,4) DEFAULT 0, -- Total Akumulasi
    book_value NUMERIC(18,4) NOT NULL, -- Nilai Buku (Cost - Accumulated)
    status VARCHAR(16) DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 3. Tabel Log Depresiasi Bulanan
CREATE TABLE fin.asset_depreciation_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    asset_id BIGINT NOT NULL REFERENCES fin.asset_master(id),
    period VARCHAR(7) NOT NULL, -- Format YYYY-MM (misal: 2026-11)
    depreciation_amount NUMERIC(18,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(asset_id, period)
);