package com.conlact.conlact_backend.service;

import java.time.Duration;

public interface AuthRateLimiter {

    void hit(String scope, String key, int max, Duration window);

    void clear(String scope, String key);
}
