package com.exchangerate.manager.exception;

/**
 * Thrown when the local AI insight-generation capability (Ollama) is unreachable, times out, or
 * otherwise fails, so the caller degrades to an explicit failure rather than a fabricated
 * narrative.
 */
public class AiInsightUnavailableException extends RuntimeException {

    public AiInsightUnavailableException(String message) {
        super(message);
    }
}
