# Exchange Rate Management System

## 1. Overview

Design and build an Exchange Rate Management System end-to-end — backend API, scheduled data
collection, Angular frontend, and a small AI-powered insight feature.

## 2. Problem Statement

Build an internal platform to consolidate how exchange rates are calculated and presented. The
platform must collect live rates, apply a spread-adjusted calculation, serve a REST API, surface the
data through an Angular dashboard, and provide a lightweight AI-generated insight layer on top of
historical rate trends.

The source of exchange rates is https://fixer.io/ (free subscription). Rates should be fetched once a
day and stored locally so the application does not depend on the external API for every query.

## 3. Technology Requirements

- **Backend:** Java 17 or later, Spring Boot, Maven, Hibernate / Spring Data JPA, any relational
  database
- **Frontend:** Angular v15 or later, TypeScript throughout
- **AI Integration:** Spring AI (preferred) or LangChain4j, connected to any open-source LLM
  (Ollama, a local model, or an OpenAI-compatible endpoint)
- **API Documentation:** Swagger / OpenAPI

## 4. Backend Requirements

Project structure, layering, libraries, and patterns are open decisions. Aim for good separation of
concerns, idiomatic use of Spring and JPA, and appropriate use of types and numeric precision
(e.g. `BigDecimal` for monetary values).

### 4.1 Data Collection

Implement a scheduled job that fetches the latest exchange rates from Fixer.io once a day at 12:05 AM
GMT and persists them to the database. The stored record must include the currency code, the rate
value, and the date the rate was calculated as reported by the API — not the system date of the fetch.
Duplicate rates for the same currency and date must be handled gracefully. Assume the service may
run as multiple instances in production; the scheduler should behave correctly in that environment.

### 4.2 Exchange Rate API

Expose a REST endpoint that accepts a source currency, a target currency, and an optional date, and
returns the spread-adjusted exchange rate for that pair. The calculation must use the rate data held
locally in the database. When no date is supplied, use the most recent available rates. When rates for
a requested date do not exist, return an appropriate HTTP error.

Every successful query must increment a usage counter for each of the two currencies involved. This
counter is used by the analytics features. The increment must be safe under concurrent requests.

The spread-adjusted calculation is described in Section 6.

### 4.3 Analytics Endpoint

Expose an endpoint that returns usage statistics — at minimum, the query count per currency and the
dates on which queries were made. The structure of this response is a design decision; it needs to
support the frontend analytics view described in Section 5.

### 4.4 Manual Refresh (Optional)

An additional endpoint to manually trigger a rate fetch and upsert — without affecting existing usage
counters — is an optional extension.

## 5. Frontend Requirements

The Angular application should be a clean, navigable single-page application that consumes the
backend API. Three views are required.

### 5.1 Exchange Rate Calculator

A view where a user can select two currencies, optionally specify a date, and see the spread-adjusted
exchange rate returned by the API. The form should behave sensibly — validated inputs, clear error
messages when the API returns an error, and a visible loading state while the request is in flight.

### 5.2 Historical Rates & Trend Chart

A view that allows the user to select a currency pair and a date range and see two things side by
side: a table of the raw exchange rates for that period, and a line chart showing how the rate moved
over time. The chart does not need to be elaborate — clarity of the trend is what matters. Below or
alongside the chart, display the AI-generated trend insight described in Section 7.

### 5.3 Usage Analytics Dashboard

A view that surfaces the data from the analytics endpoint — which currencies are queried most often,
over what time periods, and any patterns visible in the usage data. Visualization approach is open.

### 5.4 Frontend Standards

The application must run with `ng serve` pointed at the backend via a configurable environment
variable, so it can be run locally without code changes.

## 6. Exchange Rate Calculation

### 6.1 Formula

Apply the spread of whichever currency in the pair carries the higher spread:

```
adjustedRate = (toCurrencyRateToUSD / fromCurrencyRateToUSD) × ((100 − MAX(toSpread, fromSpread)) / 100)
```

### 6.2 Worked Example

| | EUR | PLN |
|---|---|---|
| Rate to USD | 0.8 | 3.7 |
| Spread | 1% | 4% |

`(3.7 / 0.8) × ((100 − 4) / 100) = 4.625 × 0.96 = 4.44`

## 7. AI-Powered Trend Insight

### 7.1 What to Build

When a user views the Historical Rates & Trend Chart (Section 5.2), a short natural language insight
about the rate trend for the selected period should be generated and displayed alongside the chart.
This insight should be produced by calling an LLM through Spring AI (or LangChain4j), with the
historical rate data for that period passed as context.

The insight should be a brief, readable commentary — something a user glancing at the chart would
find useful. It does not need to be financially precise or sophisticated. What matters is that the LLM
is genuinely receiving the rate data and responding to it, not producing a generic response.

### 7.2 Technical Expectations

Use Spring AI's chat client abstraction (or an equivalent) to call a locally-running open-source model
(Ollama is the simplest setup) or any OpenAI-compatible endpoint. The model choice is open. Key
requirements:

- The rate data for the selected period must be injected into the prompt as context — the LLM
  must be reading the actual numbers, not guessing.
- The system prompt must be designed to constrain the output to a concise, relevant insight.
- The backend must expose an endpoint that the Angular frontend calls to retrieve this insight.
  The frontend should display it cleanly, with an appropriate loading state.
- Document the model setup in the README so it can be run locally without configuration
  guesswork.

### 7.3 What Is Not Expected

Financial accuracy, a fine-tuned model, or a production-grade RAG pipeline are not expected. The
goal is correct Spring AI wiring, a thoughtful prompt, and a feature that actually works end-to-end. A
well-integrated simple solution is better than an over-engineered one that does not run.

## Appendix A — API Response Formats

**GET /exchange**

```json
{
  "from": "EUR",
  "to": "PLN",
  "exchange": 4.4405487565413254,
  "date": "2024-03-15",
  "fromQueryCount": 142,
  "toQueryCount": 37
}
```

**GET /analytics** (suggested shape — open design)

```json
{
  "topCurrencies": [
    { "currency": "EUR", "totalCount": 142, "lastQueried": "2024-03-15" },
    { "currency": "USD", "totalCount": 98,  "lastQueried": "2024-03-14" }
  ]
}
```

**GET /exchange/insight** (suggested shape — open design)

```json
{
  "from": "EUR",
  "to": "GBP",
  "fromDate": "2024-02-01",
  "toDate": "2024-03-01",
  "insight": "EUR/GBP softened by approximately 1.8% over this period, with the steepest decline in the final week of February."
}
```

## Appendix B — Currency Spread Reference

| Currency Group | Spread % |
|---|---|
| Base Currency (as returned by the Fixer.io API key) | 0.00% |
| JPY, HKD, KRW | 3.25% |
| MYR, INR, MXN | 4.50% |
| RUB, CNY, ZAR | 6.00% |
| All other currencies | 2.75% |
