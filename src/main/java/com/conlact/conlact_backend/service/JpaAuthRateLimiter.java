package com.conlact.conlact_backend.service;

import com.conlact.conlact_backend.entity.AuthRateLimit;
import com.conlact.conlact_backend.exception.RateLimitException;
import com.conlact.conlact_backend.repository.AuthRateLimitRepository;
import com.conlact.conlact_backend.security.AuthSecrets;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;

@Service
public class JpaAuthRateLimiter implements AuthRateLimiter {

    private final AuthRateLimitRepository repository;
    private final TransactionTemplate transaction;

    public JpaAuthRateLimiter(AuthRateLimitRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void hit(String scope, String key, int max, Duration window) {
        String keyHash = AuthSecrets.sha256(scope + "\n" + (key == null ? "" : key));
        try {
            transaction.executeWithoutResult(status -> apply(scope, keyHash, max, window));
        } catch (DataIntegrityViolationException ex) {
            transaction.executeWithoutResult(status -> apply(scope, keyHash, max, window));
        }
    }

    @Override
    public void clear(String scope, String key) {
        String keyHash = AuthSecrets.sha256(scope + "\n" + (key == null ? "" : key));
        transaction.executeWithoutResult(status -> repository.deleteByScopeAndKeyHash(scope, keyHash));
    }

    private void apply(String scope, String keyHash, int max, Duration window) {
        OffsetDateTime now = OffsetDateTime.now();
        AuthRateLimit row = repository.findByScopeAndKeyHash(scope, keyHash).orElse(null);
        if (row == null) {
            repository.saveAndFlush(AuthRateLimit.builder()
                    .scope(scope)
                    .keyHash(keyHash)
                    .windowStart(now)
                    .hits(1)
                    .build());
            return;
        }
        if (row.getWindowStart().plus(window).isBefore(now) || row.getWindowStart().plus(window).isEqual(now)) {
            row.setWindowStart(now);
            row.setHits(1);
            repository.save(row);
            return;
        }
        if (row.getHits() >= max) {
            long seconds = Duration.between(now, row.getWindowStart().plus(window)).toSeconds();
            throw new RateLimitException(
                    "Demasiadas solicitudes. Espera un momento e inténtalo de nuevo.",
                    (int) Math.max(seconds, 1)
            );
        }
        row.setHits(row.getHits() + 1);
        repository.save(row);
    }
}
