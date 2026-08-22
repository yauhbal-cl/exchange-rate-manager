package com.exchangerate.manager.scheduler;

import com.exchangerate.manager.service.RateCollectionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RateCollectionScheduler {

    private final RateCollectionService rateCollectionService;

    public RateCollectionScheduler(RateCollectionService rateCollectionService) {
        this.rateCollectionService = rateCollectionService;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "GMT")
    public void runScheduledCollection() {
        rateCollectionService.collect();
    }
}
