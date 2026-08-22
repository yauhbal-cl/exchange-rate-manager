package com.exchangerate.manager.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Transient (non-persisted) result of a manual-refresh collection run.
 * Pure data holder — no business logic.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RefreshResult {

    private int currenciesCollected;

    private LocalDate rateDate;
}
