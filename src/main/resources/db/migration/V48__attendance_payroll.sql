-- 1. Tambah Akun COA untuk Payroll
INSERT INTO fin.chart_of_account (tenant_id, company_id, code, name, account_type, is_header, status) VALUES
(1, 1, '6105', 'Beban Gaji Karyawan', 'EXPENSE', FALSE, 'ACTIVE'),
(1, 1, '6106', 'Beban BPJS (Perusahaan)', 'EXPENSE', FALSE, 'ACTIVE'),
(1, 1, '2203', 'Hutang Gaji', 'LIABILITY', FALSE, 'ACTIVE'),
(1, 1, '2204', 'Hutang PPh 21', 'LIABILITY', FALSE, 'ACTIVE'),
(1, 1, '2205', 'Hutang BPJS', 'LIABILITY', FALSE, 'ACTIVE'),
(1, 1, '2206', 'Hutang Titipan (Potongan)', 'LIABILITY', FALSE, 'ACTIVE')
ON CONFLICT DO NOTHING;

-- 2. Tabel Master Gaji Karyawan
CREATE TABLE hr.employee_salary (
    employee_id BIGINT PRIMARY KEY REFERENCES hr.employee(id),
    base_salary NUMERIC(18,4) NOT NULL DEFAULT 0,
    fixed_allowance NUMERIC(18,4) NOT NULL DEFAULT 0, -- Tunjangan tetap
    bpjs_kesehatan_rate NUMERIC(5,2) DEFAULT 1, -- 1% potongan karyawan
    bpjs_jht_rate NUMERIC(5,2) DEFAULT 2, -- 2% potongan karyawan
    bpjs_jp_rate NUMERIC(5,2) DEFAULT 1, -- 1% potongan karyawan
    ptkp_status VARCHAR(10) DEFAULT 'TK/0', -- Status pajak (TK/0, K/1, dll)
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- 3. Tabel Log Absensi Harian
CREATE TABLE hr.attendance (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES hr.employee(id),
    att_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('HADIR', 'SAKIT', 'IZIN', 'ALPHA', 'CUTI')),
    late_minutes INT DEFAULT 0, -- Menit keterlambatan
    remarks TEXT,
    UNIQUE(employee_id, att_date)
);

-- 4. Tabel Header Payroll Run (Satu record per bulan)
CREATE TABLE hr.payroll_run (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    period VARCHAR(7) NOT NULL UNIQUE, -- Format YYYY-MM
    total_gross NUMERIC(18,4) NOT NULL DEFAULT 0,
    total_deduction NUMERIC(18,4) NOT NULL DEFAULT 0,
    total_net NUMERIC(18,4) NOT NULL DEFAULT 0,
    status VARCHAR(16) DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'POSTED')),
    created_at TIMESTAMPTZ DEFAULT now()
);

-- 5. Tabel Detail Payroll (Slip Gaji per Karyawan)
CREATE TABLE hr.payroll_dtl (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payroll_run_id BIGINT NOT NULL REFERENCES hr.payroll_run(id) ON DELETE CASCADE,
    employee_id BIGINT NOT NULL REFERENCES hr.employee(id),
    gross_salary NUMERIC(18,4) NOT NULL,
    bpjs_deduction NUMERIC(18,4) NOT NULL DEFAULT 0,
    pph21_deduction NUMERIC(18,4) NOT NULL DEFAULT 0,
    net_salary NUMERIC(18,4) NOT NULL
);

-- 6. Menu
INSERT INTO core.menu (parent_id, code, name, path, icon, sort_order, is_header, required_permission) VALUES
((SELECT id FROM core.menu WHERE code = 'hrga'), 'hr.attendance', 'Attendance', '/ui/hr/attendance', 'calendar', 73, FALSE, 'hr.request.read'),
((SELECT id FROM core.menu WHERE code = 'hrga'), 'hr.payroll', 'Payroll', '/ui/hr/payroll', 'dollar-sign', 74, FALSE, 'hr.request.read')
ON CONFLICT (code) DO NOTHING;