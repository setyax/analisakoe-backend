-- 1. Tambah kolom Coretax di Sales Invoice
ALTER TABLE sal.sales_invoice_hdr ADD COLUMN coretax_status VARCHAR(30) DEFAULT 'PENDING';
ALTER TABLE sal.sales_invoice_hdr ADD COLUMN nsfp VARCHAR(20);
ALTER TABLE sal.sales_invoice_hdr ADD COLUMN coretax_message TEXT;

-- 2. Tabel Log untuk Audit Request/Response ke PJAP
CREATE TABLE sys.coretax_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    request_payload TEXT,
    response_payload TEXT,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);