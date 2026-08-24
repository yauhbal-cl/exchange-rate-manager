-- Development-only data for visually exercising the usage analytics dashboard.
-- This script is intentionally outside db/migration and runs only with the `dev` profile.

CREATE TABLE IF NOT EXISTS dev_seed_marker (
    seed_key VARCHAR(100) PRIMARY KEY,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM dev_seed_marker WHERE seed_key = 'usage-analytics-demo-v1'
    ) THEN
        -- The usage endpoint discovers currencies through exchange_rates, so give every demo
        -- currency a current rate. Existing locally collected rates are left untouched.
        INSERT INTO exchange_rates (currency_code, rate_to_usd, rate_date)
        VALUES
            ('EUR', 1.170000, current_date),
            ('USD', 1.000000, current_date),
            ('GBP', 1.350000, current_date),
            ('JPY', 0.006800, current_date),
            ('NOK', 0.098000, current_date)
        ON CONFLICT (currency_code, rate_date) DO NOTHING;

        -- Lifetime totals are deliberately much larger than retained event history.
        INSERT INTO currency_usage (currency_code, query_count, last_queried_at)
        VALUES
            ('EUR', 42921, now() - interval '2 minutes'),
            ('USD', 35104, now() - interval '5 minutes'),
            ('GBP', 22781, now() - interval '1 hour'),
            ('JPY',  9201, now() - interval '104 days')
        ON CONFLICT (currency_code) DO UPDATE SET
            query_count = GREATEST(currency_usage.query_count, EXCLUDED.query_count),
            last_queried_at = GREATEST(currency_usage.last_queried_at, EXCLUDED.last_queried_at);

        -- EUR is spread across the whole default window and has more than 10 entries, which
        -- exercises both the sparkline distribution and the accordion's More control.
        INSERT INTO currency_query_event (currency_code, queried_at)
        SELECT 'EUR', now() - age
        FROM unnest(ARRAY[
            interval '2 minutes', interval '20 minutes', interval '2 hours',
            interval '8 hours', interval '1 day', interval '2 days', interval '3 days',
            interval '5 days', interval '7 days', interval '10 days', interval '14 days',
            interval '18 days', interval '22 days', interval '28 days', interval '35 days',
            interval '43 days', interval '52 days', interval '61 days', interval '73 days',
            interval '82 days', interval '89 days'
        ]) AS ages(age);

        -- USD is intentionally clustered in the latest part of the window.
        INSERT INTO currency_query_event (currency_code, queried_at)
        SELECT 'USD', now() - age
        FROM unnest(ARRAY[
            interval '5 minutes', interval '12 minutes', interval '30 minutes',
            interval '1 hour', interval '3 hours', interval '8 hours', interval '16 hours',
            interval '1 day', interval '1 day 4 hours', interval '2 days',
            interval '3 days', interval '4 days', interval '5 days', interval '6 days',
            interval '7 days', interval '12 days'
        ]) AS ages(age);

        -- GBP forms a mid-window cluster with enough rows to exercise More as well.
        INSERT INTO currency_query_event (currency_code, queried_at)
        SELECT 'GBP', now() - age
        FROM unnest(ARRAY[
            interval '1 hour', interval '1 day', interval '6 days', interval '12 days',
            interval '20 days', interval '21 days', interval '22 days', interval '23 days',
            interval '24 days', interval '25 days', interval '26 days', interval '27 days',
            interval '28 days', interval '40 days'
        ]) AS ages(age);

        -- JPY is absent from 7/30/90-day history but appears in the 365-day view. NOK has no
        -- usage row at all on a clean database, producing the dashboard's Never state.
        INSERT INTO currency_query_event (currency_code, queried_at)
        VALUES
            ('JPY', now() - interval '104 days'),
            ('JPY', now() - interval '130 days'),
            ('JPY', now() - interval '180 days'),
            ('JPY', now() - interval '260 days');

        INSERT INTO dev_seed_marker (seed_key) VALUES ('usage-analytics-demo-v1');
    END IF;
END $$;
