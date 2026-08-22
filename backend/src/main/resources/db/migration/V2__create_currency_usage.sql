CREATE TABLE currency_usage (
    id BIGSERIAL PRIMARY KEY,
    currency_code CHAR(3) NOT NULL UNIQUE,
    query_count BIGINT NOT NULL DEFAULT 0,
    last_queried_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
