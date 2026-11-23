-- Header: Tipe Pajak, Total Pajak, Total Akhir
ALTER TABLE sal.sales_order_hdr ADD COLUMN tax_type VARCHAR(16) DEFAULT 'NO_TAX';
ALTER TABLE sal.sales_order_hdr ADD COLUMN tax_amount NUMERIC(18,4) DEFAULT 0;
ALTER TABLE sal.sales_order_hdr ADD COLUMN total_amount NUMERIC(18,4) DEFAULT 0;

-- Detail: Pajak per baris, Total per baris (setelah pajak)
ALTER TABLE sal.sales_order_dtl ADD COLUMN tax_amount NUMERIC(18,4) DEFAULT 0;
ALTER TABLE sal.sales_order_dtl ADD COLUMN line_total NUMERIC(18,4) DEFAULT 0;