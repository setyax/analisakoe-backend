-- 1. Hitung ulang Subtotal, DPP, Pajak, dan Total di level Detail
UPDATE sal.sales_order_dtl d
SET 
    subtotal = d.qty * d.price,
    dpp = CASE 
        WHEN (SELECT tax_type FROM sal.sales_order_hdr h WHERE h.id = d.header_id) = 'INCLUDE' 
            THEN (d.qty * d.price) / 1.11
        ELSE (d.qty * d.price)
    END,
    tax_amount = CASE 
        WHEN (SELECT tax_type FROM sal.sales_order_hdr h WHERE h.id = d.header_id) = 'EXCLUDE' 
            THEN (d.qty * d.price) * 0.11
        WHEN (SELECT tax_type FROM sal.sales_order_hdr h WHERE h.id = d.header_id) = 'INCLUDE' 
            THEN (d.qty * d.price) - ((d.qty * d.price) / 1.11)
        ELSE 0
    END,
    line_total = CASE 
        WHEN (SELECT tax_type FROM sal.sales_order_hdr h WHERE h.id = d.header_id) = 'EXCLUDE' 
            THEN (d.qty * d.price) * 1.11
        ELSE (d.qty * d.price)
    END;

-- 2. Update Total Amount dan Total Pajak di level Header
UPDATE sal.sales_order_hdr h
SET 
    tax_amount = COALESCE((SELECT SUM(d.tax_amount) FROM sal.sales_order_dtl d WHERE d.header_id = h.id), 0),
    total_amount = COALESCE((SELECT SUM(d.line_total) FROM sal.sales_order_dtl d WHERE d.header_id = h.id), 0);