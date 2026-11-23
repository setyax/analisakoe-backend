-- 1. Hapus constraint lama yang terlalu ketat
ALTER TABLE sal.sales_order_hdr DROP CONSTRAINT IF EXISTS sales_order_hdr_status_check;

-- 2. Tambahkan constraint baru yang mendukung status DELIVERED dan INVOICED
ALTER TABLE sal.sales_order_hdr ADD CONSTRAINT sales_order_hdr_status_check 
CHECK (status IN ('DRAFT', 'CONFIRMED', 'DELIVERED', 'INVOICED', 'CANCELLED'));