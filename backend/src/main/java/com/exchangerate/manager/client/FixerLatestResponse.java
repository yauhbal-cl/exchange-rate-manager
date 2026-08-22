package com.exchangerate.manager.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Jackson deserialization DTO for Fixer.io's {@code /latest} endpoint response.
 * Pure data holder — no business logic.
 */
public class FixerLatestResponse {

    private boolean success;

    private String base;

    private LocalDate date;

    private Map<String, BigDecimal> rates;

    private FixerError error;

    public FixerLatestResponse() {
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getBase() {
        return base;
    }

    public void setBase(String base) {
        this.base = base;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Map<String, BigDecimal> getRates() {
        return rates;
    }

    public void setRates(Map<String, BigDecimal> rates) {
        this.rates = rates;
    }

    public FixerError getError() {
        return error;
    }

    public void setError(FixerError error) {
        this.error = error;
    }

    /**
     * Fixer.io's documented error envelope, present only when {@code success = false}.
     */
    public static class FixerError {

        private int code;

        private String type;

        private String info;

        public FixerError() {
        }

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getInfo() {
            return info;
        }

        public void setInfo(String info) {
            this.info = info;
        }
    }
}
