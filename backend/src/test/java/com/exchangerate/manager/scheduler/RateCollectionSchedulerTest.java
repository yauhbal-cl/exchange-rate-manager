package com.exchangerate.manager.scheduler;

import com.exchangerate.manager.service.RateCollectionException;
import com.exchangerate.manager.service.RateCollectionService;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RateCollectionSchedulerTest {

    @Test
    void handlesServiceLevelCollectionFailure() {
        RateCollectionService rateCollectionService = mock(RateCollectionService.class);
        doThrow(new RateCollectionException("provider unavailable"))
                .when(rateCollectionService)
                .collect();
        RateCollectionScheduler scheduler = new RateCollectionScheduler(rateCollectionService);

        assertThatCode(scheduler::runScheduledCollection).doesNotThrowAnyException();
        verify(rateCollectionService).collect();
    }
}
