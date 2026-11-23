-- 1. Tambahan Field di Level Header (PR)
ALTER TABLE pur.purchase_request_hdr ADD COLUMN doc_type VARCHAR(20) DEFAULT 'STANDARD';
ALTER TABLE pur.purchase_request_hdr ADD COLUMN purchasing_org VARCHAR(20) DEFAULT '1000';
ALTER TABLE pur.purchase_request_hdr ADD COLUMN purchasing_group VARCHAR(20) DEFAULT '001';
ALTER TABLE pur.purchase_request_hdr ADD COLUMN requisitioner VARCHAR(50);

-- 2. Tambahan Field di Level Detail (Baris Item PR)
ALTER TABLE pur.purchase_request_dtl ADD COLUMN short_text VARCHAR(255);
ALTER TABLE pur.purchase_request_dtl ADD COLUMN delivery_date DATE;
ALTER TABLE pur.purchase_request_dtl ADD COLUMN estimated_price NUMERIC(18,4) DEFAULT 0;
ALTER TABLE pur.purchase_request_dtl ADD COLUMN account_assignment VARCHAR(30);
ALTER TABLE pur.purchase_request_dtl ADD COLUMN material_group VARCHAR(30);
ALTER TABLE pur.purchase_request_dtl ADD COLUMN converted_po_id BIGINT;