package com.conlact.conlact_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "auth_rate_limits",
        uniqueConstraints = @UniqueConstraint(name = "uq_auth_rate_scope_key", columnNames = {"scope", "key_hash"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthRateLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "scope", nullable = false, length = 48)
    private String scope;

    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    @Column(name = "window_start", nullable = false)
    private OffsetDateTime windowStart;

    @Column(name = "hits", nullable = false)
    private int hits;
}
