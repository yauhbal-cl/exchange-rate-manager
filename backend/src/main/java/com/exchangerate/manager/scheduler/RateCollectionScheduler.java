package com.exchangerate.manager.scheduler;

import com.exchangerate.manager.client.FixerApiException;
import com.exchangerate.manager.service.RateCollectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RateCollectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RateCollectionScheduler.class);

    private final RateCollectionService rateCollectionService;

    public RateCollectionScheduler(RateCollectionService rateCollectionService) {
        this.rateCollectionService = rateCollectionService;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "GMT")
    public void runScheduledCollection() {
        try {
            rateCollectionService.collect();
        } catch (FixerApiException e) {
            log.error("Scheduled Fixer.io rate collection failed: {}", e.getMessage(), e);
        }
    }
}
