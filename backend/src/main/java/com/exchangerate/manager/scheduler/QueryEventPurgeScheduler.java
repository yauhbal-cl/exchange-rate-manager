package com.exchangerate.manager.scheduler;

import com.exchangerate.manager.service.QueryEventPurgeService;

import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QueryEventPurgeScheduler {

    private final QueryEventPurgeService queryEventPurgeService;

    @Scheduled(cron = "0 30 2 * * *", zone = "GMT")
    public void runScheduledPurge() {
        queryEventPurgeService.purgeExpiredEvents();
    }
}
