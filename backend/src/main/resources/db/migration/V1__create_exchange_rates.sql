CREATE TABLE exchange_rates (
    id             BIGSERIAL PRIMARY KEY,
    currency_code  CHAR(3) NOT NULL CHECK (currency_code ~ '^[A-Z]{3}$'),
    rate_to_usd    NUMERIC(19,6) NOT NULL CHECK (rate_to_usd > 0),
    rate_date      DATE NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_exchange_rates_currency_date
    ON exchange_rates (currency_code, rate_date);
