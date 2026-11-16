package com.conlact.conlact_backend.exception;

public class RateLimitException extends RuntimeException {

    private final int retryAfterSeconds;

    public RateLimitException(String message, int retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = Math.max(retryAfterSeconds, 1);
    }

    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
