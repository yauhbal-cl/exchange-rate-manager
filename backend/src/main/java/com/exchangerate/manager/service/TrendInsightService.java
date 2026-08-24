package com.exchangerate.manager.service;

import com.exchangerate.manager.exception.AiInsightUnavailableException;
import com.exchangerate.manager.exception.RateDataNotFoundException;
import com.exchangerate.manager.exception.TrendRangeTooLargeException;
import com.exchangerate.manager.exception.UnknownCurrencyException;
import com.exchangerate.manager.repository.ExchangeRateRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Generates a short, data-grounded AI narrative describing the trend in the raw, unadjusted
 * historical cross-rate series between two currencies over a resolved date range.
 *
 * <p>The raw historical cross-rates for the resolved range are serialized verbatim (dates +
 * values) into the prompt sent to the local Ollama model via Spring AI's {@link ChatClient} — no
 * RAG, no fine-tuning. If the AI model is unreachable, times out, or otherwise fails, this
 * degrades to {@link AiInsightUnavailableException} rather than fabricating a narrative.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrendInsightService {

    private static final int MAX_TREND_POINTS = 365;

    private static final String SYSTEM_PROMPT = loadSystemPrompt();

    private final ExchangeRateService exchangeRateService;

    private final ExchangeRateRepository exchangeRateRepository;

    private final ChatClient chatClient;

    private static String loadSystemPrompt() {
        try {
            return new ClassPathResource("prompts/trend-insight-system.st")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load trend insight system prompt", e);
        }
    }

    public TrendInsightResult generateInsight(String from, String to, LocalDate startDate, LocalDate endDate) {
        if (!exchangeRateRepository.existsByCurrencyCode(from)) {
            throw new UnknownCurrencyException("Unknown currency code: " + from);
        }
        if (!exchangeRateRepository.existsByCurrencyCode(to)) {
            throw new UnknownCurrencyException("Unknown currency code: " + to);
        }

        DateRangeResolver.DateRange dateRange = DateRangeResolver.resolve(startDate, endDate);
        LocalDate effectiveStartDate = dateRange.startDate();
        LocalDate effectiveEndDate = dateRange.endDate();

        long dayCount = ChronoUnit.DAYS.between(effectiveStartDate, effectiveEndDate) + 1;
        if (dayCount > MAX_TREND_POINTS) {
            throw new TrendRangeTooLargeException(
                    "Requested range " + effectiveStartDate + " to " + effectiveEndDate + " spans "
                            + dayCount + " days, which exceeds the maximum of " + MAX_TREND_POINTS
                            + " daily points supported for AI trend insight generation");
        }

        List<RateTrendPoint> trendPoints =
                exchangeRateService.getTrend(from, to, effectiveStartDate, effectiveEndDate);
        if (trendPoints.isEmpty()) {
            throw new RateDataNotFoundException(
                    "No rate data found for currencies '" + from + "' and '" + to + "' between "
                            + effectiveStartDate + " and " + effectiveEndDate);
        }

        String userMessage = buildUserMessage(trendPoints);

        String narrative;
        try {
            narrative = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userMessage)
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("AI trend insight generation failed for {}/{} between {} and {}: {}",
                    from, to, effectiveStartDate, effectiveEndDate, e.getMessage(), e);
            throw new AiInsightUnavailableException("AI insight generation is currently unavailable");
        }

        return new TrendInsightResult(from, to, effectiveStartDate, effectiveEndDate, narrative);
    }

    private static String buildUserMessage(List<RateTrendPoint> trendPoints) {
        StringBuilder builder = new StringBuilder();
        for (RateTrendPoint point : trendPoints) {
            builder.append(point.rateDate().format(DateTimeFormatter.ISO_LOCAL_DATE))
                    .append(": ")
                    .append(point.rate().toPlainString())
                    .append('\n');
        }
        return builder.toString();
    }
}
