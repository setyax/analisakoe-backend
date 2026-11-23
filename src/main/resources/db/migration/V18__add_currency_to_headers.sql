-- Sales
ALTER TABLE sal.sales_order_hdr ADD COLUMN currency CHAR(3) DEFAULT 'IDR';
ALTER TABLE sal.delivery_order_hdr ADD COLUMN currency CHAR(3) DEFAULT 'IDR';
ALTER TABLE sal.sales_invoice_hdr ADD COLUMN currency CHAR(3) DEFAULT 'IDR';

-- Purchasing
ALTER TABLE pur.purchase_request_hdr ADD COLUMN currency CHAR(3) DEFAULT 'IDR';
ALTER TABLE pur.purchase_order_hdr ADD COLUMN currency CHAR(3) DEFAULT 'IDR';
ALTER TABLE pur.grn_hdr ADD COLUMN currency CHAR(3) DEFAULT 'IDR';
ALTER TABLE pur.purchase_invoice_hdr ADD COLUMN currency CHAR(3) DEFAULT 'IDR';