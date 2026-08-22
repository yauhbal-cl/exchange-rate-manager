CREATE TABLE currency_usage (
    id BIGSERIAL PRIMARY KEY,
    currency_code CHAR(3) NOT NULL UNIQUE CHECK (currency_code ~ '^[A-Z]{3}$'),
    query_count BIGINT NOT NULL DEFAULT 0 CHECK (query_count >= 0),
    last_queried_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
