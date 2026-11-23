-- Add UOM to Master Item Price.
-- Existing price records inherit the item's Base UOM when available.

ALTER TABLE md.item_price
ADD COLUMN IF NOT EXISTS uom_id BIGINT;

UPDATE md.item_price ip
SET uom_id = i.base_uom_id
FROM md.item i
WHERE i.id = ip.item_id
  AND ip.uom_id IS NULL
  AND i.base_uom_id IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_item_price_uom'
          AND conrelid = 'md.item_price'::regclass
    ) THEN
        ALTER TABLE md.item_price
        ADD CONSTRAINT fk_item_price_uom
        FOREIGN KEY (uom_id)
        REFERENCES md.uom(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_item_price_uom
ON md.item_price(uom_id);
