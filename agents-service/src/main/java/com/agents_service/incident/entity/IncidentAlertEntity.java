package com.agents_service.incident.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident_alerts")
@Getter
@Setter
@NoArgsConstructor
public class IncidentAlertEntity {

    @Id
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "target_id", nullable = false, length = 100)
    private String targetId;

    @Column(name = "fingerprint", nullable = false, length = 255)
    private String fingerprint;

    @Column(name = "alert_name", nullable = false, length = 255)
    private String alertName;

    @Column(name = "alert_status", nullable = false, length = 20)
    private String alertStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "alert_payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode alertPayload = JsonNodeFactory.instance.objectNode();

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @PrePersist
    void generateDefaults() {
        if (id == null) {
            id = UUID.randomUUID();
        }

        Instant now = Instant.now();

        if (firstSeenAt == null) {
            firstSeenAt = now;
        }

        if (lastSeenAt == null) {
            lastSeenAt = now;
        }
    }
}