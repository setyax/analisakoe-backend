ALTER TABLE sal.sales_order_dtl ADD COLUMN subtotal NUMERIC(18,4) DEFAULT 0;
ALTER TABLE sal.sales_order_dtl ADD COLUMN dpp NUMERIC(18,4) DEFAULT 0;