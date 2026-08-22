package com.exchangerate.manager.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RateTrendPoint(
    LocalDate rateDate,
    BigDecimal rate
) {}
