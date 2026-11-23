-- 1. Hapus constraint lama yang kaku
ALTER TABLE pur.purchase_invoice_hdr DROP CONSTRAINT IF EXISTS purchase_invoice_hdr_status_check;

-- 2. Tambahkan constraint baru yang mendukung VOID
ALTER TABLE pur.purchase_invoice_hdr ADD CONSTRAINT purchase_invoice_hdr_status_check 
CHECK (status IN ('DRAFT', 'POSTED', 'PAID', 'VOID'));

-- 3. Lakukan hal yang sama untuk Sales Invoice agar aman saat di-Void
ALTER TABLE sal.sales_invoice_hdr DROP CONSTRAINT IF EXISTS sales_invoice_hdr_status_check;
ALTER TABLE sal.sales_invoice_hdr ADD CONSTRAINT sales_invoice_hdr_status_check 
CHECK (status IN ('DRAFT', 'POSTED', 'PAID', 'VOID'));