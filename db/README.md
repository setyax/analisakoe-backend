# Database Setup — AnalisaKoe ERP

Backend butuh PostgreSQL (dikembangkan di PostgreSQL 15+, port default dev: **5433**).

## Struktur database

| Schema | Isi |
|---|---|
| `core` | company, users, roles, permissions |
| `md` | master data (item, customer, supplier, uom, warehouse, salesperson, ...) |
| `pur` | purchasing (PO, GRN, Purchase Invoice, retur, RFQ) |
| `sal` | sales (SO, DO, SI, retur, quotation) |
| `inv` | inventory (stock_balance, stock_movement, transaction) |
| `fin` | finance (chart_of_account, journal, cash_bank_trx, bank_account, ar/ap) |
| `hr` | karyawan, payroll, cash advance, attendance |

## Cara setup di mesin baru

```bash
# 1. Buat database
psql -U postgres -c "CREATE DATABASE analisakoe"

# 2. Muat struktur lengkap
psql -U postgres -d analisakoe -f db/schema.sql

# 3. Muat data setup minimum (company, COA, UoM, warehouse, dll.)
psql -U postgres -d analisakoe -f db/seed_minimal.sql
```

`schema.sql` dibuat dengan `pg_dump --schema-only` (struktur penuh, tanpa data).
`seed_minimal.sql` berisi data master minimum agar aplikasi langsung jalan
(PT. AnalisaKoe Jakarta = company id 11 adalah company aktif utama).

## Hal penting dalam data seed

- **COA kode kunci** untuk jurnal otomatis: `1201` Piutang, `1301` Persediaan,
  `2101` Hutang Usaha, `2105` GR/IR Clearing, `2201` Hutang PPN,
  `4101` Pendapatan Penjualan, `5101` HPP
- **UoM Set PACK-STD**: 1 DUS = 12 PCS, 1 BOX = 12 PCS (set `KEMASAN01` datanya
  salah dan sengaja tidak dipakai — jangan tautkan item ke set itu)
- **Fiscal period** harus OPEN untuk company terkait, kalau tidak jurnal akan
  ditolak ("Fiscal Period ... belum dibuka")

## Konfigurasi aplikasi

`src/main/resources/application.yml` — sesuaikan `spring.datasource.url`,
`username`, `password`.

## Backup berkala

```bash
pg_dump -h localhost -p 5433 -U postgres -d analisakoe -f backup_$(date +%F).sql
```
