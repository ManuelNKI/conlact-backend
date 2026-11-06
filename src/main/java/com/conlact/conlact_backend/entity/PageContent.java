package com.conlact.conlact_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.Map;

@Entity
@Table(name = "page_contents")
@Getter @Setter @NoArgsConstructor
public class PageContent {
    @Id private String slug;
    private String eyebrow;
    @Column(nullable = false) private String titulo;
    private String subtitulo;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> contenido;
    @UpdateTimestamp private OffsetDateTime actualizadoEn;
}
