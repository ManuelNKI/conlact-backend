package com.conlact.conlact_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_change_challenges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailChangeChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "request_id", nullable = false, unique = true, length = 64)
    private String requestId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "current_email", nullable = false, length = 254)
    private String currentEmail;

    @Column(name = "new_email", nullable = false, length = 254)
    private String newEmail;

    /** SHA-256 hexadecimal del código de seis dígitos. Nunca el código en claro. */
    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private int maxAttempts = 5;

    @Column(name = "is_consumed", nullable = false)
    @Builder.Default
    private boolean isConsumed = false;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "resend_after")
    private OffsetDateTime resendAfter;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
