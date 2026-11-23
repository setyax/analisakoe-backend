-- 1. Tabel Referensi Kode Barang/Jasa DJP
CREATE TABLE md.djp_goods_code (
    code VARCHAR(10) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT
);

-- Seed beberapa kode DJP (contoh)
INSERT INTO md.djp_goods_code (code, name, description) VALUES
('000000', 'Barang Umum', 'Barang kena pajak pada umumnya'),
('010101', 'Kendaraan Bermotor', 'Mobil, motor, dll'),
('020202', 'Jasa Konsultan', 'Jasa konsultasi manajemen'),
('030303', 'Jasa Perbaikan', 'Jasa servis dan perbaikan');

-- 2. Tambah kolom di Master Item untuk menyimpan Kode DJP
ALTER TABLE md.item ADD COLUMN djp_goods_code VARCHAR(10) DEFAULT '000000' REFERENCES md.djp_goods_code(code);