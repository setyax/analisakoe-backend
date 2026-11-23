ALTER TABLE sal.sales_return_hdr ADD COLUMN warehouse_id BIGINT REFERENCES md.warehouse(id);
ALTER TABLE sal.sales_return_hdr ADD COLUMN tax_amount NUMERIC(18,4) DEFAULT 0;