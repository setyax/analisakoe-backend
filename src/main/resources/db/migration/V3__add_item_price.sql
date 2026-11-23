CREATE TABLE md.item_price (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL REFERENCES core.company(id),
    item_id BIGINT NOT NULL REFERENCES md.item(id),
    currency CHAR(3) NOT NULL REFERENCES md.currency(code),
    price NUMERIC(18,4) NOT NULL CHECK (price >= 0),
    valid_from DATE NOT NULL,
    valid_to DATE,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE','INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (valid_to IS NULL OR valid_to > valid_from)
);

CREATE INDEX idx_item_price_item ON md.item_price(item_id, valid_from);
CREATE INDEX idx_item_price_company ON md.item_price(company_id);
CREATE INDEX idx_item_price_active ON md.item_price(item_id)
    WHERE status = 'ACTIVE';