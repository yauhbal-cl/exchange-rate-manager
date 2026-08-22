package com.exchangerate.manager.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

@Entity
@Table(name = "currency_usage")
public class CurrencyUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotNull
    @Pattern(regexp = "^[A-Z]{3}$")
    @Column(name = "currency_code", nullable = false, unique = true, length = 3)
    private String currencyCode;

    @NotNull
    @PositiveOrZero
    @Column(name = "query_count", nullable = false)
    private Long queryCount;

    @NotNull
    @Column(name = "last_queried_at", nullable = false)
    private Instant lastQueriedAt;

    public CurrencyUsage() {
    }

    public CurrencyUsage(Long id, String currencyCode, Long queryCount, Instant lastQueriedAt) {
        this.id = id;
        this.currencyCode = currencyCode;
        this.queryCount = queryCount;
        this.lastQueriedAt = lastQueriedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public Long getQueryCount() {
        return queryCount;
    }

    public void setQueryCount(Long queryCount) {
        this.queryCount = queryCount;
    }

    public Instant getLastQueriedAt() {
        return lastQueriedAt;
    }

    public void setLastQueriedAt(Instant lastQueriedAt) {
        this.lastQueriedAt = lastQueriedAt;
    }
}
