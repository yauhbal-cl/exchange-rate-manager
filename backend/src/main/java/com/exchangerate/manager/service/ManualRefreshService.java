package com.exchangerate.manager.service;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

/** Coordinates a user-requested rate collection run. */
@Service
@RequiredArgsConstructor
public class ManualRefreshService {

    private static final String COLLECTION_IN_PROGRESS_MESSAGE =
            "A collection run is already in progress; try again shortly.";

    private final RateCollectionService rateCollectionService;

    public RefreshResult refresh() {
        RefreshResult result = rateCollectionService.collect();
        if (result == null) {
            throw new CollectionInProgressException(COLLECTION_IN_PROGRESS_MESSAGE);
        }
        return result;
    }
}
