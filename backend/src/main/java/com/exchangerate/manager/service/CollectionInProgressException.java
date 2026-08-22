package com.exchangerate.manager.service;

/**
 * Thrown when a manual refresh is requested while a collection run (scheduled or manual) already
 * holds the ShedLock, so {@link RateCollectionService#collect()} returned {@code null} instead of
 * running.
 */
public class CollectionInProgressException extends RuntimeException {

    public CollectionInProgressException(String message) {
        super(message);
    }
}
