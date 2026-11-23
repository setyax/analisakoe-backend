ALTER TABLE pur.price_list
    ADD COLUMN IF NOT EXISTS uom_id BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_price_list_uom'
          AND conrelid = 'pur.price_list'::regclass
    ) THEN
        ALTER TABLE pur.price_list
            ADD CONSTRAINT fk_price_list_uom
            FOREIGN KEY (uom_id) REFERENCES md.uom(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_price_list_uom
    ON pur.price_list(uom_id);