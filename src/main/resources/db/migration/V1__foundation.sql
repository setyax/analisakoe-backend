CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gin;
CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE SCHEMA IF NOT EXISTS core;
CREATE SCHEMA IF NOT EXISTS sys;
CREATE SCHEMA IF NOT EXISTS md;
CREATE SCHEMA IF NOT EXISTS fin;
CREATE SCHEMA IF NOT EXISTS inv;
CREATE SCHEMA IF NOT EXISTS pur;
CREATE SCHEMA IF NOT EXISTS sal;
CREATE SCHEMA IF NOT EXISTS mf;
CREATE SCHEMA IF NOT EXISTS pos;
CREATE SCHEMA IF NOT EXISTS hr;
CREATE SCHEMA IF NOT EXISTS rpt;

CREATE TABLE core.tenant (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE core.company (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES core.tenant(id),
    code VARCHAR(20) NOT NULL,
    name VARCHAR(200) NOT NULL,
    tax_id VARCHAR(50),
    base_currency CHAR(3) NOT NULL DEFAULT 'IDR',
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Jakarta',
    fiscal_year_start_month SMALLINT NOT NULL DEFAULT 1 CHECK (fiscal_year_start_month BETWEEN 1 AND 12),
    parent_id BIGINT REFERENCES core.company(id),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED','CLOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, code)
);
CREATE INDEX idx_company_tenant ON core.company(tenant_id);

CREATE TABLE core.app_user (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES core.tenant(id),
    username VARCHAR(64) NOT NULL,
    email VARCHAR(255),
    password_hash TEXT NOT NULL,
    full_name VARCHAR(200),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','LOCKED','DISABLED')),
    must_change_pwd BOOLEAN NOT NULL DEFAULT FALSE,
    failed_attempts SMALLINT NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    last_login TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, username)
);
CREATE INDEX idx_user_tenant ON core.app_user(tenant_id);
CREATE INDEX idx_user_email ON core.app_user(LOWER(email));
CREATE INDEX idx_user_status ON core.app_user(status) WHERE status <> 'ACTIVE';

CREATE TABLE core.role (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES core.tenant(id),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, code)
);

CREATE TABLE core.permission (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    module VARCHAR(30) NOT NULL,
    description TEXT
);

CREATE TABLE core.user_role (
    user_id BIGINT NOT NULL REFERENCES core.app_user(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES core.role(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE core.role_permission (
    role_id BIGINT NOT NULL REFERENCES core.role(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES core.permission(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE core.user_company (
    user_id BIGINT NOT NULL REFERENCES core.app_user(id) ON DELETE CASCADE,
    company_id BIGINT NOT NULL REFERENCES core.company(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, company_id)
);

CREATE TABLE core.audit_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT,
    user_id BIGINT,
    schema_name VARCHAR(20) NOT NULL,
    table_name VARCHAR(64) NOT NULL,
    record_id TEXT NOT NULL,
    action VARCHAR(10) NOT NULL CHECK (action IN ('INSERT','UPDATE','DELETE')),
    old_data JSONB,
    new_data JSONB,
    remote_ip INET,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);
CREATE INDEX idx_audit_record ON core.audit_log(schema_name, table_name, record_id);
CREATE INDEX idx_audit_user ON core.audit_log(user_id, created_at DESC);

DO $$
DECLARE
    start_date DATE := date_trunc('month', now())::date;
    i INT;
    part_name TEXT;
    from_date DATE;
    to_date DATE;
BEGIN
    FOR i IN 0..2 LOOP
        from_date := start_date + (i || ' month')::interval;
        to_date := from_date + interval '1 month';
        part_name := 'audit_log_' || to_char(from_date, 'YYYY_MM');
        EXECUTE format('CREATE TABLE IF NOT EXISTS core.%I PARTITION OF core.audit_log FOR VALUES FROM (%L) TO (%L)', part_name, from_date, to_date);
    END LOOP;
END $$;

CREATE TABLE sys.app_config (
    tenant_id BIGINT NOT NULL,
    company_id BIGINT,
    config_key VARCHAR(100) NOT NULL,
    config_value TEXT,
    data_type VARCHAR(16) NOT NULL DEFAULT 'STRING' CHECK (data_type IN ('STRING','INT','DECIMAL','BOOLEAN','JSON','DATE')),
    description TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, company_id, config_key)
);

CREATE TABLE sys.doc_number_counter (
    company_id BIGINT NOT NULL REFERENCES core.company(id),
    doc_type VARCHAR(30) NOT NULL,
    period VARCHAR(6) NOT NULL,
    prefix VARCHAR(20) NOT NULL,
    last_number BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (company_id, doc_type, period)
);

CREATE TABLE sys.job (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_name VARCHAR(100) NOT NULL UNIQUE,
    job_group VARCHAR(50) NOT NULL,
    cron_expr VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_run TIMESTAMPTZ,
    last_status VARCHAR(16),
    last_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sys.outbox_event (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id TEXT NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PROCESSING','DONE','FAILED')),
    attempts SMALLINT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pending ON sys.outbox_event(next_attempt_at) WHERE status IN ('PENDING','FAILED');

CREATE TABLE md.currency (
    code CHAR(3) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    symbol VARCHAR(10),
    decimal_places SMALLINT NOT NULL DEFAULT 2
);

CREATE TABLE md.uom (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    UNIQUE (tenant_id, code)
);

CREATE TABLE md.uom_conversion (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    from_uom_id BIGINT NOT NULL REFERENCES md.uom(id),
    to_uom_id BIGINT NOT NULL REFERENCES md.uom(id),
    factor NUMERIC(18,6) NOT NULL CHECK (factor > 0),
    UNIQUE (tenant_id, from_uom_id, to_uom_id)
);

CREATE TABLE md.tax (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    rate NUMERIC(8,4) NOT NULL CHECK (rate >= 0),
    tax_type VARCHAR(16) NOT NULL CHECK (tax_type IN ('VAT','WHT','OTHER')),
    UNIQUE (tenant_id, code)
);

CREATE TABLE md.item_category (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(200) NOT NULL,
    parent_id BIGINT REFERENCES md.item_category(id),
    UNIQUE (tenant_id, code)
);

CREATE TABLE md.item (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL REFERENCES core.company(id),
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category_id BIGINT REFERENCES md.item_category(id),
    base_uom_id BIGINT NOT NULL REFERENCES md.uom(id),
    item_type VARCHAR(16) NOT NULL DEFAULT 'STOCK' CHECK (item_type IN ('STOCK','NONSTOCK','SERVICE')),
    track_lot BOOLEAN NOT NULL DEFAULT FALSE,
    track_serial BOOLEAN NOT NULL DEFAULT FALSE,
    barcode VARCHAR(50),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, company_id, code)
);
CREATE INDEX idx_item_category ON md.item(category_id);
CREATE INDEX idx_item_name_trgm ON md.item USING gin (name gin_trgm_ops);
CREATE INDEX idx_item_barcode ON md.item(barcode) WHERE barcode IS NOT NULL;

CREATE TABLE md.warehouse (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL REFERENCES core.company(id),
    code VARCHAR(20) NOT NULL,
    name VARCHAR(200) NOT NULL,
    address TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    UNIQUE (tenant_id, company_id, code)
);

CREATE TABLE md.customer (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL REFERENCES core.company(id),
    code VARCHAR(20) NOT NULL,
    name VARCHAR(255) NOT NULL,
    tax_id VARCHAR(50),
    currency CHAR(3) REFERENCES md.currency(code),
    credit_limit NUMERIC(18,4) NOT NULL DEFAULT 0,
    payment_term_days SMALLINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE','BLOCKED')),
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, company_id, code)
);

CREATE TABLE md.supplier (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL REFERENCES core.company(id),
    code VARCHAR(20) NOT NULL,
    name VARCHAR(255) NOT NULL,
    tax_id VARCHAR(50),
    currency CHAR(3) REFERENCES md.currency(code),
    payment_term_days SMALLINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE','BLOCKED')),
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, company_id, code)
);

CREATE TABLE fin.chart_of_account (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL REFERENCES core.company(id),
    code VARCHAR(30) NOT NULL,
    name VARCHAR(200) NOT NULL,
    account_type VARCHAR(16) NOT NULL CHECK (account_type IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE')),
    parent_id BIGINT REFERENCES fin.chart_of_account(id),
    is_header BOOLEAN NOT NULL DEFAULT FALSE,
    currency CHAR(3),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, company_id, code)
);
CREATE INDEX idx_coa_type ON fin.chart_of_account(account_type);
CREATE INDEX idx_coa_parent ON fin.chart_of_account(parent_id);

CREATE OR REPLACE FUNCTION core.set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END $$;

DO $$
DECLARE r RECORD;
BEGIN
    FOR r IN
        SELECT schemaname, tablename FROM pg_tables
        WHERE schemaname IN ('core','md','fin','sys')
          AND tablename NOT IN ('audit_log','outbox_event','job','permission','role_permission','user_role','user_company')
    LOOP
        EXECUTE format('CREATE TRIGGER trg_%I_updated BEFORE UPDATE ON %I.%I FOR EACH ROW EXECUTE FUNCTION core.set_updated_at()', r.tablename, r.schemaname, r.tablename);
    END LOOP;
END $$;