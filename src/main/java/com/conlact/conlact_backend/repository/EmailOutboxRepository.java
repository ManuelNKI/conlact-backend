package com.conlact.conlact_backend.repository;

import com.conlact.conlact_backend.entity.EmailOutbox;
import com.conlact.conlact_backend.entity.enums.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {

    Optional<EmailOutbox> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT e FROM EmailOutbox e WHERE e.status = :status AND (e.nextRetryAt IS NULL OR e.nextRetryAt <= :now) ORDER BY e.createdAt ASC")
    List<EmailOutbox> findPendingEmails(
            @Param("status") OutboxStatus status,
            @Param("now") OffsetDateTime now,
            Pageable pageable
    );

    long countByStatus(OutboxStatus status);
}
