package com.exchangerate.manager.service;

import com.exchangerate.manager.repository.ExchangeRateRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link TrendInsightService}. Mocks {@link ExchangeRateService},
 * {@link ExchangeRateRepository}, and the {@link ChatClient} fluent API — no Spring context, per
 * research.md's plain-JUnit-with-Mockito approach for pure service-layer logic. Covers only the
 * happy paths of US1 (Acceptance Scenario 1) and FR-007 (single-data-point generation).
 */
@ExtendWith(MockitoExtension.class)
class TrendInsightServiceTest {

    @Mock
    private ExchangeRateService exchangeRateService;

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @InjectMocks
    private TrendInsightService trendInsightService;

    private void stubChatClient(String narrative) {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(narrative);
    }

    @Test
    void generateInsightReturnsNarrativeGroundedInSuppliedRatePoints() {
        LocalDate startDate = LocalDate.of(2026, 8, 1);
        LocalDate endDate = LocalDate.of(2026, 8, 3);

        List<RateTrendPoint> trendPoints = List.of(
                new RateTrendPoint(LocalDate.of(2026, 8, 1), new BigDecimal("1.080000")),
                new RateTrendPoint(LocalDate.of(2026, 8, 2), new BigDecimal("1.081500")),
                new RateTrendPoint(LocalDate.of(2026, 8, 3), new BigDecimal("1.079800")));

        String narrative = "The EUR/USD rate held broadly steady over the period, with a slight "
                + "uptick mid-range before easing back near its starting level.";

        when(exchangeRateRepository.existsByCurrencyCode("EUR")).thenReturn(true);
        when(exchangeRateRepository.existsByCurrencyCode("USD")).thenReturn(true);
        when(exchangeRateService.getTrend("EUR", "USD", startDate, endDate)).thenReturn(trendPoints);
        stubChatClient(narrative);

        TrendInsightResult result = trendInsightService.generateInsight("EUR", "USD", startDate, endDate);

        assertThat(result).isNotNull();
        assertThat(result.narrative()).isEqualTo(narrative);
        assertThat(result.fromCurrency()).isEqualTo("EUR");
        assertThat(result.toCurrency()).isEqualTo("USD");
        assertThat(result.startDate()).isEqualTo(startDate);
        assertThat(result.endDate()).isEqualTo(endDate);

        ArgumentCaptor<String> userMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(userMessageCaptor.capture());
        String capturedUserMessage = userMessageCaptor.getValue();

        for (RateTrendPoint point : trendPoints) {
            assertThat(capturedUserMessage).contains(point.rateDate().toString());
            assertThat(capturedUserMessage).contains(point.rate().toPlainString());
        }
    }

    @Test
    void generateInsightWithSingleDataPointStillReturnsAResult() {
        LocalDate startDate = LocalDate.of(2026, 8, 20);
        LocalDate endDate = LocalDate.of(2026, 8, 20);

        List<RateTrendPoint> trendPoints =
                List.of(new RateTrendPoint(LocalDate.of(2026, 8, 20), new BigDecimal("1.080000")));

        String narrative = "The EUR/USD rate was observed at 1.080000 on 2026-08-20.";

        when(exchangeRateRepository.existsByCurrencyCode("EUR")).thenReturn(true);
        when(exchangeRateRepository.existsByCurrencyCode("USD")).thenReturn(true);
        when(exchangeRateService.getTrend("EUR", "USD", startDate, endDate)).thenReturn(trendPoints);
        stubChatClient(narrative);

        TrendInsightResult result = trendInsightService.generateInsight("EUR", "USD", startDate, endDate);

        assertThat(result).isNotNull();
        assertThat(result.narrative()).isEqualTo(narrative);

        verify(chatClient).prompt();
        verify(requestSpec).call();
        verify(callResponseSpec).content();
    }
}
