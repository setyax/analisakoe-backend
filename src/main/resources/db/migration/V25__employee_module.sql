-- 1. Buat Schema HR (Jika belum ada)
CREATE SCHEMA IF NOT EXISTS hr;

-- 2. Buat Tabel Master Karyawan
CREATE TABLE hr.employee (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    nip VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    department VARCHAR(50),
    position VARCHAR(50),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 3. Ubah kolom requisitioner di PR agar menjadi ID Karyawan
ALTER TABLE pur.purchase_request_hdr DROP COLUMN IF EXISTS requisitioner;
ALTER TABLE pur.purchase_request_hdr ADD COLUMN requisitioner_id BIGINT REFERENCES hr.employee(id);

-- 4. Seed Data Karyawan (Contoh)
INSERT INTO hr.employee (tenant_id, company_id, nip, name, department, position) VALUES
(1, 1, 'EMP-001', 'Budi Santoso', 'IT', 'Staff IT'),
(1, 1, 'EMP-002', 'Siti Aminah', 'HRGA', 'Staff HR'),
(1, 1, 'EMP-003', 'Andi Wijaya', 'Marketing', 'Supervisor');