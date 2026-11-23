-- 1. Tambah kolom status jika belum ada
ALTER TABLE pur.grn_hdr ADD COLUMN IF NOT EXISTS status VARCHAR(16) DEFAULT 'DRAFT';

-- 2. Hapus constraint lama jika ada (untuk berjaga-jaga)
ALTER TABLE pur.grn_hdr DROP CONSTRAINT IF EXISTS grn_hdr_status_check;

-- 3. Set status default untuk data lama (jika ada)
UPDATE pur.grn_hdr SET status = 'CONFIRMED' WHERE status = 'POSTED';

-- 4. Tambahkan constraint baru yang mendukung DRAFT, CONFIRMED, VOID
ALTER TABLE pur.grn_hdr ADD CONSTRAINT grn_hdr_status_check 
CHECK (status IN ('DRAFT', 'CONFIRMED', 'VOID'));