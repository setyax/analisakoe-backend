-- 1. Purchasing Group (Menentukan buyer penanggung jawab)
ALTER TABLE md.item ADD COLUMN purchasing_group VARCHAR(20) DEFAULT '001';

-- 2. Planned Delivery Time / Lead Time (Dalam satuan hari)
ALTER TABLE md.item ADD COLUMN lead_time_days INTEGER DEFAULT 0;

-- 3. Price Control (S = Standard Price, V = Moving Average)
ALTER TABLE md.item ADD COLUMN price_control CHAR(1) DEFAULT 'V' CHECK (price_control IN ('S', 'V'));

-- 4. Valuation Class (Untuk mapping ke akun GL/COA)
ALTER TABLE md.item ADD COLUMN valuation_class VARCHAR(20) DEFAULT '3000';

-- 5. Standard Price (Wajib diisi jika Price Control = 'S')
ALTER TABLE md.item ADD COLUMN standard_price NUMERIC(18,4) DEFAULT 0;