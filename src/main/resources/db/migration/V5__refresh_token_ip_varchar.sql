ALTER TABLE core.refresh_token
    ALTER COLUMN ip_address TYPE VARCHAR(45)
    USING ip_address::text;