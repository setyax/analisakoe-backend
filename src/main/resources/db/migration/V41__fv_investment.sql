-- 1. Tambah Akun COA untuk Gain/Loss FVTPL (di bawah Pendapatan Lain & Beban Lain)
INSERT INTO fin.chart_of_account (tenant_id, company_id, code, name, account_type, is_header, status) VALUES
(1, 1, '8101', 'Keuntungan Selisih Nilai Wajar', 'REVENUE', FALSE, 'ACTIVE'),
(1, 1, '8102', 'Kerugian Selisih Nilai Wajar', 'EXPENSE', FALSE, 'ACTIVE'),
(1, 1, '1104', 'Investasi (FVTPL)', 'ASSET', FALSE, 'ACTIVE')
ON CONFLICT DO NOTHING;

-- 2. Tabel Investasi (Aset yang diukur pada Nilai Wajar)
CREATE TABLE fin.investment (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    acquisition_date DATE NOT NULL,
    book_value NUMERIC(18,4) NOT NULL, -- Nilai Tercatat Saat Ini
    last_fair_value NUMERIC(18,4) NOT NULL, -- Nilai Wajar Terakhir
    status VARCHAR(16) DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed Data Investasi (Contoh: Saham PT ABC)
INSERT INTO fin.investment (tenant_id, company_id, code, name, acquisition_date, book_value, last_fair_value) VALUES
(1, 1, 'INV-001', 'Saham PT ABC Tbk', CURRENT_DATE, 100000000, 100000000);