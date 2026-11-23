package id.analisakoe.web.ui;

/**
 * Sumber movement untuk report dan historical.
 * Satu baris per stock_movement. Transaksi manual TRX-* yang belum mempunyai
 * stock_movement_id ditambahkan dari transaction_detail agar tidak hilang.
 * DO/GRN yang sudah mempunyai stock_movement_id tidak dihitung dua kali.
 */
final class InventoryMovementQuery {
    private InventoryMovementQuery() {}

    static final String CTE = """
        WITH movement_source AS (
            SELECT
                sm.tenant_id,
                NULL::bigint AS source_company_id,
                sm.movement_date,
                sm.item_id,
                sm.warehouse_id,
                UPPER(sm.direction) AS direction,
                sm.qty,
                sm.unit_cost,
                sm.total_cost,
                sm.balance_qty,
                sm.reference_type,
                sm.reference_id,
                sm.reference_no,
                sm.remarks,
                sm.id AS source_row_id,
                'STOCK_MOVEMENT'::varchar AS source_kind
            FROM inv.stock_movement sm

            UNION ALL

            SELECT
                th.tenant_id,
                th.company_id AS source_company_id,
                th.doc_date AS movement_date,
                td.item_id,
                th.warehouse_id,
                UPPER(th.doc_type) AS direction,
                td.qty,
                NULL::numeric AS unit_cost,
                NULL::numeric AS total_cost,
                NULL::numeric AS balance_qty,
                ('MANUAL_' || UPPER(th.doc_type))::varchar AS reference_type,
                th.id AS reference_id,
                th.doc_no AS reference_no,
                th.remarks,
                td.id AS source_row_id,
                'TRANSACTION_DETAIL'::varchar AS source_kind
            FROM inv.transaction_header th
            JOIN inv.transaction_detail td ON td.header_id = th.id
            WHERE UPPER(COALESCE(th.status, '')) IN ('POSTED', 'CONFIRMED')
              AND UPPER(COALESCE(th.doc_type, '')) IN ('IN', 'OUT')
              AND th.doc_no LIKE 'TRX-%'
              AND td.stock_movement_id IS NULL
        )
        """;
}