CREATE TABLE currency_query_event (
    id BIGSERIAL PRIMARY KEY,
    currency_code CHAR(3) NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'),
    queried_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_currency_query_event_code_queried_at ON currency_query_event (currency_code, queried_at);

INSERT INTO currency_query_event (currency_code, queried_at)
SELECT currency_code, last_queried_at FROM currency_usage;
