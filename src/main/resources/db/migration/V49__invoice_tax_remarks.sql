ALTER TABLE sal.sales_invoice_hdr ADD COLUMN tax_amount NUMERIC(18,4) DEFAULT 0;
ALTER TABLE sal.sales_invoice_hdr ADD COLUMN remarks TEXT;