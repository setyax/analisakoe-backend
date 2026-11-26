-- ============================================================
-- AnalisaKoe ERP — Data Setup Minimum (idempotent, jalankan SETELAH schema.sql)
-- Target: PostgreSQL. Ubah password sesuai lingkungan Anda.
-- Catatan: company utama = PT. AnalisaKoe Jakarta (id 11).
-- ============================================================

-- 1) COMPANIES (company lain SUSPENDED, sesuai keputusan implementasi)
INSERT INTO core.company (id, code, name, status) VALUES
  (1,  'MAIN', 'Main Company', 'SUSPENDED'),
  (3,  '01',   'PT. ABC', 'SUSPENDED'),
  (5,  '02',   'PT. DEF', 'SUSPENDED'),
  (6,  '03',   'PT. FGH', 'SUSPENDED'),
  (9,  'SBY',  'PT. AnalisaKoe Surabaya', 'SUSPENDED'),
  (10, 'MLG',  'PT. Analisakoe Malang', 'SUSPENDED'),
  (11, 'JKT',  'PT. AnalisaKoe Jakarta', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

-- 2) UoM
INSERT INTO md.uom (id, code, name) VALUES
  (1,'PCS','Piece'),(2,'BOX','Box'),(3,'KG','Kilogram'),(4,'LTR','Liter'),
  (5,'MTR','Meter'),(6,'DOZ','Dozen'),(10,'UNIT','Unit'),(11,'DUS','Dus'),
  (12,'SACK','Sack'),(34,'PALET','Palet'),(35,'GRAM','Gram'),(37,'TON','Ton'),
  (38,'ML','Mililiter'),(39,'LITER','Liter'),(40,'GALON','Galon'),(41,'DRUM','Drum'),
  (42,'MM','Milimeter'),(43,'CM','Centimeter'),(44,'M','Meter'),(45,'KM','Kilometer')
ON CONFLICT (id) DO NOTHING;

-- 3) UoM SET (perhatian: set 1 KEMASAN01 datanya salah — 1 DUS = 1 PCS. Tidak dipakai item mana pun; set benar = PACK-STD)
INSERT INTO md.uom_set (id, code, name) VALUES
  (1,'KEMASAN01','Kemasan Standar'),(2,'PACK-STD','Kemasan Standar'),
  (3,'WEIGHT-STD','Berat Standar'),(4,'VOL-STD','Volume Standar'),
  (5,'LENGTH-STD','Panjang Standar'),(6,'UNIT','UNIT')
ON CONFLICT (id) DO NOTHING;

INSERT INTO md.uom_set_dtl (uom_set_id, uom_id, conversion_factor, is_base) VALUES
  (2, 1,  1.0, true),  -- PCS = base
  (2, 2,  12.0, false),-- 1 BOX = 12 PCS
  (2, 11, 12.0, false),-- 1 DUS = 12 PCS
  (2, 34, 50.0, false),-- 1 PALET = 50 PCS
  (3, 35, 1.0, true),(3, 3, 1000.0, false),(3, 37, 1000.0, false),
  (4, 38, 1.0, true),(4, 39, 1000.0, false),(4, 40, 19.0, false),(4, 41, 200.0, false),
  (5, 42, 1.0, true),(5, 43, 10.0, false),(5, 44, 100.0, false),(5, 45, 1000.0, false),
  (6, 10, 1.0, true)
ON CONFLICT DO NOTHING;

-- 4) WAREHOUSE (company 11)
INSERT INTO md.warehouse (id, code, name, company_id, status) VALUES
  (1,'MAIN','GUDANG PUSAT',11,'ACTIVE'),
  (2,'WH02','GUDANG BS',11,'ACTIVE'),
  (3,'WH01','GUDANG TRADE',11,'ACTIVE'),
  (5,'WH03','GUDANG ATK',11,'ACTIVE')
ON CONFLICT (id) DO NOTHING;

-- 5) COA company 11 (kode penting untuk otomasi jurnal:
--    1201 Piutang | 1301 Persediaan | 2101 Hutang | 2105 GR/IR Clearing
--    2201 PPN | 4101 Pendapatan | 5101 HPP | 1102 Bank BCA)
INSERT INTO fin.chart_of_account (id, code, name, company_id, is_header) VALUES
  (1,'1000','Aset',11,true),(7,'1101','Kas Kecil',11,false),(8,'1102','Bank BCA',11,false),
  (24,'1103','Piutang Karyawan',11,false),(28,'1104','Investasi (FVTPL)',11,false),
  (9,'1201','Piutang Usaha',11,false),(10,'1301','Persediaan Barang Dagang',11,false),
  (11,'1401','Peralatan Kantor',11,false),(30,'1402','Aset Tetap - Bangunan',11,false),
  (31,'1403','Aset Tetap - Kendaraan',11,false),(32,'1404','Aset Tetap - Peralatan & Mesin',11,false),
  (33,'1405','Aset Tetap - Perabotan Kantor',11,false),(34,'1491','Akumulasi Depresiasi Aset',11,false),
  (2,'2000','Kewajiban',11,true),(12,'2101','Hutang Usaha',11,false),(69,'2105','GR/IR Clearing',11,false),
  (13,'2201','Hutang PPN',11,false),(14,'2202','Hutang PPh 21',11,false),(44,'2203','Hutang Gaji',11,false),
  (45,'2204','Hutang PPh 21',11,false),(46,'2205','Hutang BPJS',11,false),(47,'2206','Hutang Titipan (Potongan)',11,false),
  (3,'3000','Ekuitas',11,true),(15,'3101','Modal Disetor',11,false),(16,'3201','Laba Ditahan',11,false),
  (17,'3301','Laba Tahun Berjalan',11,false),
  (4,'4000','Pendapatan',11,true),(18,'4101','Pendapatan Penjualan',11,false),(36,'4102','Retur Penjualan',11,false),
  (38,'4103','Pendapatan Lain-lain',11,false),(19,'4201','Diskon Penjualan',11,false),
  (5,'5000','Harga Pokok Penjualan',11,true),(20,'5101','Beban Pokok Penjualan',11,false),(37,'5102','Retur Pembelian',11,false),
  (6,'6000','Beban Operasional',11,true),
  (21,'6101','Beban Gaji Karyawan',11,false),(22,'6102','Beban Listrik dan Air',11,false),
  (23,'6103','Beban Sewa Kantor',11,false),(25,'6104','Beban Perjalanan Dinas',11,false),
  (42,'6105','Beban Gaji Karyawan',11,false),(43,'6106','Beban BPJS (Perusahaan)',11,false),
  (55,'6107','Beban BBM',11,false),(56,'6108','Beban Parkir & Tol',11,false),
  (57,'6109','Beban Bonus Karyawan',11,false),(58,'6110','Beban THR (Tunjangan Hari Raya)',11,false),
  (59,'6111','Beban Konsumsi & Jamuan',11,false),(60,'6112','Beban ATK & Perlengkapan',11,false),
  (61,'6113','Beban Komunikasi (Pulsa/Internet)',11,false),(62,'6114','Beban Pemeliharaan & Perbaikan',11,false),
  (63,'6115','Beban Pengiriman & Ekspedisi',11,false),(64,'6116','Beban Perjalanan Dinas',11,false),
  (65,'6117','Beban Asuransi',11,false),(66,'6118','Beban Pajak & Retribusi',11,false),
  (67,'6119','Beban Perlengkapan Kantor',11,false),(68,'6120','Beban Lain-lain',11,false),
  (41,'6197','Beban Selisih Persediaan',11,false),(39,'6198','Beban Lain-lain',11,false),
  (35,'6199','Beban Depresiasi',11,false),
  (26,'8101','Keuntungan Selisih Nilai Wajar',11,false),(27,'8102','Kerugian Selisih Nilai Wajar',11,false),
  (40,'8103','Pendapatan Selisih Persediaan',11,false)
ON CONFLICT (id) DO NOTHING;

-- 6) SALESPERSON
INSERT INTO md.salesperson (id, code, name, is_active) VALUES
  (1,'SALES01','SALES 01',true),(2,'SALES02','SALES 02',true),(3,'SALES03','SALES 03',true)
ON CONFLICT (id) DO NOTHING;

-- 7) FISCAL PERIOD company 11 — buka periode berjalan (ubah sesuai bulan mulai)
-- Contoh: buka 12 bulan 2026
INSERT INTO fin.fiscal_period (company_id, period, status)
SELECT 11, to_char(m, 'YYYY-MM'), 'OPEN'
FROM generate_series(DATE '2026-01-01', DATE '2026-12-01', INTERVAL '1 month') m
ON CONFLICT DO NOTHING;
