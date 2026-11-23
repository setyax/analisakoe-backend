-- 1. Perkuat Tabel Company (Company Code di SAP)
ALTER TABLE core.company ADD COLUMN country_code CHAR(2) NOT NULL DEFAULT 'ID';
ALTER TABLE core.company ADD COLUMN local_currency CHAR(3) NOT NULL DEFAULT 'IDR';
ALTER TABLE core.company ADD COLUMN chart_of_accounts_id BIGINT;

-- 2. Tabel Exchange Rate (Kurs)
CREATE TABLE md.exchange_rate (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    from_currency CHAR(3) NOT NULL REFERENCES md.currency(code),
    to_currency CHAR(3) NOT NULL REFERENCES md.currency(code),
    rate NUMERIC(18,8) NOT NULL CHECK (rate > 0),
    effective_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, from_currency, to_currency, effective_date)
);

CREATE INDEX idx_xrate_lookup ON md.exchange_rate(from_currency, to_currency, effective_date DESC);

-- 3. Seed Kurs Awal (Contoh: USD ke IDR)
INSERT INTO md.exchange_rate (tenant_id, from_currency, to_currency, rate, effective_date) VALUES
(1, 'USD', 'IDR', 15500.0000, CURRENT_DATE),
(1, 'EUR', 'IDR', 16500.0000, CURRENT_DATE),
(1, 'SGD', 'IDR', 11500.0000, CURRENT_DATE),
(1, 'IDR', 'USD', 0.0000645, CURRENT_DATE);

-- 4. Permission & Menu
INSERT INTO core.permission (code, module, description) VALUES
    ('md.xrate.read', 'md', 'Lihat Kurs'),
    ('md.xrate.write', 'md', 'Kelola Kurs')
ON CONFLICT (code) DO NOTHING;

INSERT INTO core.role_permission (role_id, permission_id)
SELECT 1, id FROM core.permission WHERE code LIKE 'md.xrate%' ON CONFLICT DO NOTHING;

INSERT INTO core.menu (parent_id, code, name, path, icon, sort_order, is_header, required_permission) VALUES
    ((SELECT id FROM core.menu WHERE code = 'masterdata'), 'md.xrates', 'Exchange Rates', '/ui/exchange-rates', 'trending-up', 27, FALSE, 'md.xrate.read')
ON CONFLICT (code) DO NOTHING;