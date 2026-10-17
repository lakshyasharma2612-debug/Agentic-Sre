package com.agents_service.incident.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incident_context")
@Getter
@Setter
@NoArgsConstructor
public class IncidentContextEntity {

    @Id
    @Column(name = "incident_id")
    private UUID incidentId;

    @Column(name = "rolling_summary", nullable = false)
    private String rollingSummary = "";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recent_messages", nullable = false, columnDefinition = "jsonb")
    private JsonNode recentMessages = JsonNodeFactory.instance.arrayNode();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "current_hypotheses", nullable = false, columnDefinition = "jsonb")
    private JsonNode currentHypotheses = JsonNodeFactory.instance.arrayNode();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_ids", nullable = false, columnDefinition = "jsonb")
    private JsonNode evidenceIds = JsonNodeFactory.instance.arrayNode();

    @Column(name = "step_count", nullable = false)
    private int stepCount = 0;

    @Version
    @Column(nullable = false)
    private long version;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
