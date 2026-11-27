-- SAP-aligned purchase price list:
--   company_id     -> level organisasi pembelian (purchasing organization)
--   condition_type -> tipe kondisi (PB00 = harga dasar)
--   price_unit     -> harga per N unit (SAP "price unit" / PUN)
-- Harga lama (tenant 1) adalah data company 11 Jakarta.

ALTER TABLE pur.price_list ADD COLUMN IF NOT EXISTS company_id BIGINT;
UPDATE pur.price_list SET company_id = 11 WHERE company_id IS NULL;
ALTER TABLE pur.price_list ALTER COLUMN company_id SET NOT NULL;

ALTER TABLE pur.price_list ADD COLUMN IF NOT EXISTS condition_type VARCHAR(10) NOT NULL DEFAULT 'PB00';
ALTER TABLE pur.price_list ADD COLUMN IF NOT EXISTS price_unit NUMERIC(19,4) NOT NULL DEFAULT 1;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_price_list_company') THEN
        ALTER TABLE pur.price_list
            ADD CONSTRAINT fk_price_list_company
            FOREIGN KEY (company_id) REFERENCES core.company(id);
    END IF;
END $$;

-- Satu condition record per kombinasi kunci. Mencegah harga ganda/ambigu.
CREATE UNIQUE INDEX IF NOT EXISTS uq_price_list_condition
    ON pur.price_list (
        tenant_id, company_id, supplier_id, item_id,
        COALESCE(uom_id, 0), COALESCE(currency, 'IDR'),
        valid_from, COALESCE(valid_to, DATE '9999-12-31'),
        COALESCE(min_qty, 1), condition_type
    );

CREATE INDEX IF NOT EXISTS idx_price_list_company_lookup
    ON pur.price_list (tenant_id, company_id, supplier_id, item_id, uom_id, valid_from, valid_to);

-- Asal harga pada baris PO: PRICE_LIST / RFQ / CONTRACT / MANUAL.
ALTER TABLE pur.purchase_order_dtl ADD COLUMN IF NOT EXISTS price_source VARCHAR(20) DEFAULT 'PRICE_LIST';
ALTER TABLE pur.purchase_order_dtl ADD COLUMN IF NOT EXISTS condition_type VARCHAR(10) DEFAULT 'PB00';
ALTER TABLE pur.purchase_order_dtl ADD COLUMN IF NOT EXISTS price_unit NUMERIC(19,4) DEFAULT 1;
UPDATE pur.purchase_order_dtl SET price_source = 'PRICE_LIST' WHERE price_source IS NULL;
