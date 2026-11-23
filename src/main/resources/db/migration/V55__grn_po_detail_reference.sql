-- V55: Link every GRN detail to its exact Purchase Order detail.
-- Existing GRN rows are backfilled only when exactly one candidate exists.
-- Ambiguous or unmatched records abort the migration.

DO $$
DECLARE
    v_unmatched integer;
    v_ambiguous integer;
    v_invalid integer;
BEGIN
    -- Check that every existing GRN line has exactly one matching PO line.
    SELECT
        COUNT(*) FILTER (WHERE match_count = 0),
        COUNT(*) FILTER (WHERE match_count > 1)
    INTO v_unmatched, v_ambiguous
    FROM (
        SELECT
            gd.id,
            COUNT(pd.id) AS match_count
        FROM pur.grn_dtl gd
        JOIN pur.grn_hdr gh ON gh.id = gd.header_id
        LEFT JOIN pur.purchase_order_dtl pd
            ON pd.header_id = gh.po_id
           AND pd.item_id = gd.item_id
           AND pd.uom_id IS NOT DISTINCT FROM gd.uom_id
           AND pd.qty = gd.qty_received
        WHERE gd.po_detail_id IS NULL
        GROUP BY gd.id
    ) matches;

    IF v_unmatched > 0 OR v_ambiguous > 0 THEN
        RAISE EXCEPTION
            'V55 aborted: unmatched GRN lines=%, ambiguous GRN lines=%',
            v_unmatched, v_ambiguous;
    END IF;

    -- Backfill only exact unique matches.
    WITH candidates AS (
        SELECT
            gd.id AS grn_detail_id,
            MIN(pd.id) AS po_detail_id,
            COUNT(pd.id) AS match_count
        FROM pur.grn_dtl gd
        JOIN pur.grn_hdr gh ON gh.id = gd.header_id
        JOIN pur.purchase_order_dtl pd
            ON pd.header_id = gh.po_id
           AND pd.item_id = gd.item_id
           AND pd.uom_id IS NOT DISTINCT FROM gd.uom_id
           AND pd.qty = gd.qty_received
        WHERE gd.po_detail_id IS NULL
        GROUP BY gd.id
        HAVING COUNT(pd.id) = 1
    )
    UPDATE pur.grn_dtl gd
    SET po_detail_id = c.po_detail_id
    FROM candidates c
    WHERE gd.id = c.grn_detail_id;

    -- Validate all populated references against their GRN header,
    -- PO header, item and UOM.
    SELECT COUNT(*)
    INTO v_invalid
    FROM pur.grn_dtl gd
    JOIN pur.grn_hdr gh ON gh.id = gd.header_id
    LEFT JOIN pur.purchase_order_dtl pd
        ON pd.id = gd.po_detail_id
    WHERE gd.po_detail_id IS NOT NULL
      AND (
          pd.id IS NULL
          OR pd.header_id <> gh.po_id
          OR pd.item_id <> gd.item_id
          OR pd.uom_id IS DISTINCT FROM gd.uom_id
      );

    IF v_invalid > 0 THEN
        RAISE EXCEPTION
            'V55 aborted: % invalid GRN-to-PO detail references',
            v_invalid;
    END IF;
END $$;

-- Add FK only if it does not already exist.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'pur.grn_dtl'::regclass
          AND conname = 'grn_dtl_po_detail_id_fkey'
    ) THEN
        ALTER TABLE pur.grn_dtl
            ADD CONSTRAINT grn_dtl_po_detail_id_fkey
            FOREIGN KEY (po_detail_id)
            REFERENCES pur.purchase_order_dtl(id)
            NOT VALID;
    END IF;
END $$;

ALTER TABLE pur.grn_dtl
    VALIDATE CONSTRAINT grn_dtl_po_detail_id_fkey;

CREATE INDEX IF NOT EXISTS ix_grn_dtl_po_detail_id
    ON pur.grn_dtl(po_detail_id);