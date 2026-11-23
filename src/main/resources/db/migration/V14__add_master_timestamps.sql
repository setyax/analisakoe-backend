-- Menambahkan kolom timestamps ke tabel master data yang belum punya
-- agar trigger core.set_updated_at() tidak error saat update.

-- 1. Warehouse
ALTER TABLE md.warehouse ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE md.warehouse ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- 2. UOM
ALTER TABLE md.uom ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE md.uom ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- 3. Currency
ALTER TABLE md.currency ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE md.currency ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- 4. Item Category
ALTER TABLE md.item_category ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE md.item_category ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- 5. Tax
ALTER TABLE md.tax ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE md.tax ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();