-- 1. Tabel Jurnal Header
CREATE TABLE fin.journal_hdr (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    doc_no VARCHAR(50) NOT NULL,
    doc_date DATE NOT NULL,
    journal_type VARCHAR(30) NOT NULL, -- SALES, PURCHASE, AR_COLLECT, AP_PAY
    remarks TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 2. Tabel Jurnal Detail (Buku Besar)
CREATE TABLE fin.journal_dtl (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    header_id BIGINT NOT NULL REFERENCES fin.journal_hdr(id) ON DELETE CASCADE,
    account_id BIGINT NOT NULL REFERENCES fin.chart_of_account(id),
    debit NUMERIC(18,4) NOT NULL DEFAULT 0,
    credit NUMERIC(18,4) NOT NULL DEFAULT 0,
    description VARCHAR(255)
);

CREATE INDEX idx_journal_date ON fin.journal_hdr(doc_date);
CREATE INDEX idx_journal_acc ON fin.journal_dtl(account_id);