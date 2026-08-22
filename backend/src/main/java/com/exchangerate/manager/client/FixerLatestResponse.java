package com.exchangerate.manager.client;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Jackson deserialization DTO for Fixer.io's {@code /latest} endpoint response.
 * Pure data holder — no business logic.
 */
@Getter
@Setter
@NoArgsConstructor
public class FixerLatestResponse {

    private boolean success;

    private String base;

    private LocalDate date;

    private Map<String, BigDecimal> rates;

    private FixerError error;

    /**
     * Fixer.io's documented error envelope, present only when {@code success = false}.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class FixerError {

        private int code;

        private String type;

        private String info;
    }
}
