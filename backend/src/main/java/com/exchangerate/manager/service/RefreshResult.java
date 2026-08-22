package com.exchangerate.manager.service;

import java.time.LocalDate;

/**
 * Transient (non-persisted) result of a manual-refresh collection run.
 * Pure data holder — no business logic.
 */
public class RefreshResult {

    private int currenciesCollected;

    private LocalDate rateDate;

    public RefreshResult() {
    }

    public RefreshResult(int currenciesCollected, LocalDate rateDate) {
        this.currenciesCollected = currenciesCollected;
        this.rateDate = rateDate;
    }

    public int getCurrenciesCollected() {
        return currenciesCollected;
    }

    public void setCurrenciesCollected(int currenciesCollected) {
        this.currenciesCollected = currenciesCollected;
    }

    public LocalDate getRateDate() {
        return rateDate;
    }

    public void setRateDate(LocalDate rateDate) {
        this.rateDate = rateDate;
    }
}
