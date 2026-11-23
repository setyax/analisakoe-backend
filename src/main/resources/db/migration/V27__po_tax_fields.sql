-- Header PO
ALTER TABLE pur.purchase_order_hdr ADD COLUMN tax_type VARCHAR(16) DEFAULT 'NO_TAX';
ALTER TABLE pur.purchase_order_hdr ADD COLUMN tax_amount NUMERIC(18,4) DEFAULT 0;
ALTER TABLE pur.purchase_order_hdr ADD COLUMN total_amount NUMERIC(18,4) DEFAULT 0;

-- Detail PO
ALTER TABLE pur.purchase_order_dtl ADD COLUMN subtotal NUMERIC(18,4) DEFAULT 0;
ALTER TABLE pur.purchase_order_dtl ADD COLUMN dpp NUMERIC(18,4) DEFAULT 0;
ALTER TABLE pur.purchase_order_dtl ADD COLUMN tax_amount NUMERIC(18,4) DEFAULT 0;
ALTER TABLE pur.purchase_order_dtl ADD COLUMN line_total NUMERIC(18,4) DEFAULT 0;