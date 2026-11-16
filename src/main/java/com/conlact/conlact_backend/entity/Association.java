package com.conlact.conlact_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "associations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Association {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "short_description")
    private String shortDescription;

    @Column(name = "history", columnDefinition = "text")
    private String history;

    @Column(name = "location_text")
    private String locationText;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "arcsa_registration")
    private String arcsaRegistration;

    @Column(name = "agrocalidad_registration")
    private String agrocalidadRegistration;

    @Column(name = "sanitary_seal_text")
    private String sanitarySealText;

    @Column(name = "video_url")
    private String videoUrl;

    @Column(name = "instagram_url")
    private String instagramUrl;

    @Column(name = "tiktok_url")
    private String tiktokUrl;

    @Column(name = "facebook_url")
    private String facebookUrl;

    @Column(name = "whatsapp")
    private String whatsapp;

    @Column(name = "parish")
    private String parish;

    @Column(name = "members")
    private Integer members;

    @Column(name = "altitude_msnm")
    private Integer altitudeMsnm;

    @Column(name = "number_of_families")
    private Integer numberOfFamilies;

    @Column(name = "business_hours")
    private String businessHours;

    @Column(name = "bpm_registration")
    private String bpmRegistration;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fields_json", columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> fieldsJson = new ArrayList<>();

    @Column(name = "is_published", nullable = false)
    @Builder.Default
    private Boolean isPublished = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
