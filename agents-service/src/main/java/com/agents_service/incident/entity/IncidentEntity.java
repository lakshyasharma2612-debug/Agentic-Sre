
package com.agents_service.incident.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tools.jackson.databind.JsonNode;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incidents")
@Getter
@Setter
@NoArgsConstructor
public class IncidentEntity {

    @Id
    private UUID id;

    @Column(name = "target_id", nullable = false, length = 100)
    private String targetId;

    @Column(name = "dedup_key", nullable = false, length = 500)
    private String dedupKey;

    @Column(name = "alert_fingerprint")
    private String alertFingerprint;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "alert_payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode alertPayload;

    @Column(nullable = false, length = 40)
    private String state;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void generateId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
