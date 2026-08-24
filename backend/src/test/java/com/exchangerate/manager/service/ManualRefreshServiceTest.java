package com.exchangerate.manager.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualRefreshServiceTest {

    @Mock
    private RateCollectionService rateCollectionService;

    @InjectMocks
    private ManualRefreshService manualRefreshService;

    @Test
    void returnsSuccessfulCollectionResult() {
        RefreshResult expected = new RefreshResult(5, LocalDate.of(2026, 8, 22));
        when(rateCollectionService.collect()).thenReturn(expected);

        assertThat(manualRefreshService.refresh()).isSameAs(expected);
    }

    @Test
    void translatesSkippedLockedCollectionToConflictException() {
        when(rateCollectionService.collect()).thenReturn(null);

        assertThatThrownBy(() -> manualRefreshService.refresh())
                .isInstanceOf(CollectionInProgressException.class)
                .hasMessage("A collection run is already in progress; try again shortly.");
    }
}
