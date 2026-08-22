package com.exchangerate.manager.scheduler;

import com.exchangerate.manager.client.FixerApiException;
import com.exchangerate.manager.service.RateCollectionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateCollectionScheduler {

    private final RateCollectionService rateCollectionService;

    @Scheduled(cron = "0 5 0 * * *", zone = "GMT")
    public void runScheduledCollection() {
        try {
            rateCollectionService.collect();
        } catch (FixerApiException e) {
            log.error("Scheduled Fixer.io rate collection failed: {}", e.getMessage(), e);
        }
    }
}
