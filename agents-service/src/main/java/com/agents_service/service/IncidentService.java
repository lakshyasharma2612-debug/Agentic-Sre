package com.agents_service.service;

import com.agents_service.incident.entity.IncidentContextEntity;
import com.agents_service.incident.entity.IncidentEntity;
import com.agents_service.incident.entity.IncidentEventEntity;
import com.agents_service.incident.entity.IncidentEvidenceEntity;
import com.agents_service.incident.entity.IncidentState;
import com.agents_service.incident.repository.IncidentContextRepository;
import com.agents_service.incident.repository.IncidentEvidenceRepository;
import com.agents_service.incident.repository.IncidentEventRepository;
import com.agents_service.incident.repository.IncidentRepository;
import com.agents_service.recovery.RecoveryPolicy;
import com.agents_service.recovery.RecoveryPolicyProperties;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import jakarta.persistence.EntityNotFoundException;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.agents_service.recovery.RecoveryEvaluation;
import com.agents_service.recovery.RecoveryPolicy;
import com.agents_service.recovery.RecoveryStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IncidentService {
    private final RecoveryPolicyProperties recoveryPolicyProperties;
    private final IncidentRepository incidentRepository;
    private final IncidentContextRepository contextRepository;
    private final IncidentEventRepository eventRepository;
    private final IncidentEvidenceRepository evidenceRepository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    private static final Set<String> TERMINAL_STATES = Set.of(
            "RESOLVED",
            "ESCALATED",
            "FAILED",
            "TIMED_OUT",
            "SUPERSEDED"
    );
    public enum RecoveryApplyResult {
    APPLIED,
    LEASE_LOST,
    INCIDENT_NOT_VERIFYING
}

    /**
     * Returns an existing active incident for the same target and
     * deduplication key, or creates a new incident and context.
     */
   
        @Transactional
        public IncidentEntity openOrGetIncident(
                String targetId,
                String dedupKey,
                String alertFingerprint,
                JsonNode alertPayload) {

        return openOrGetIncidentResult(
                targetId,
                dedupKey,
                alertFingerprint,
                alertPayload
        ).incident();
        }

        @Transactional
        public IncidentOpenResult openOrGetIncidentResult(
                String targetId,
                String dedupKey,
                String alertFingerprint,
                JsonNode alertPayload) {

        if (targetId == null || targetId.isBlank()
                || dedupKey == null || dedupKey.isBlank()) {
                throw new IllegalArgumentException(
                        "targetId and dedupKey are required");
        }

        acquireDeduplicationLock(targetId, dedupKey);

        var existing = incidentRepository
                .findFirstByTargetIdAndDedupKeyAndStateNotInOrderByCreatedAtDesc(
                        targetId,
                        dedupKey,
                        Set.copyOf(TERMINAL_STATES));

        if (existing.isPresent()) {
                recordEvent(
                        existing.get().getId(),
                        "DUPLICATE_ALERT_RECEIVED",
                        JsonNodeFactory.instance.objectNode()
                );

                return new IncidentOpenResult(existing.get(), false);
        }

        IncidentEntity incident = new IncidentEntity();
        incident.setTargetId(targetId);
        incident.setDedupKey(dedupKey);
        incident.setAlertFingerprint(alertFingerprint);
        incident.setAlertPayload(
                alertPayload == null
                        ? JsonNodeFactory.instance.objectNode()
                        : alertPayload.deepCopy()
        );
        incident.setState("INVESTIGATING");

        IncidentEntity savedIncident = incidentRepository.saveAndFlush(incident);

        IncidentContextEntity context = new IncidentContextEntity();
        context.setIncidentId(savedIncident.getId());
        context.setRollingSummary("");
        context.setRecentMessages(JsonNodeFactory.instance.arrayNode());
        context.setCurrentHypotheses(JsonNodeFactory.instance.arrayNode());
        context.setEvidenceIds(JsonNodeFactory.instance.arrayNode());
        context.setStepCount(0);

        contextRepository.save(context);
        
        jdbcTemplate.update("""
                INSERT INTO incident_investigation_jobs
                        (incident_id, status)
                VALUES (?, 'PENDING')
                ON CONFLICT (incident_id) DO NOTHING
                """,
                savedIncident.getId()
        );


        ObjectNode details = JsonNodeFactory.instance.objectNode();
        details.put("targetId", targetId);
        details.put("dedupKey", dedupKey);
        details.put("state", "INVESTIGATING");

        recordEvent(savedIncident.getId(), "INCIDENT_CREATED", details);
        
        ObjectNode queuedDetails =
                JsonNodeFactory.instance.objectNode();

        queuedDetails.put("status", "PENDING");

        recordEvent(
                savedIncident.getId(),
                "INVESTIGATION_QUEUED",
                queuedDetails
        );


        return new IncidentOpenResult(savedIncident, true);
        }


    /**
     * Records an append-only audit event.
     */
    @Transactional
    public void recordEvent(
            UUID incidentId,
            String eventType,
            JsonNode details) {

        requireIncident(incidentId);

        IncidentEventEntity event = new IncidentEventEntity();
        event.setIncidentId(incidentId);
        event.setEventType(eventType);
        event.setDetails(
                details == null
                        ? JsonNodeFactory.instance.objectNode()
                        : details.deepCopy()
        );

        eventRepository.save(event);
    }

    /**
     * Stores a compact evidence record and adds its ID to the context.
     */
    @Transactional
    public UUID recordEvidence(
            UUID incidentId,
            String sourceTool,
            String summary,
            JsonNode findings,
            boolean truncated,
            String rawReference) {

        requireIncident(incidentId);

        IncidentEvidenceEntity evidence = new IncidentEvidenceEntity();
        evidence.setIncidentId(incidentId);
        evidence.setSourceTool(sourceTool);
        evidence.setSummary(summary);
        evidence.setFindings(
                findings == null
                        ? JsonNodeFactory.instance.objectNode()
                        : findings.deepCopy()
        );
        evidence.setTruncated(truncated);
        evidence.setRawReference(rawReference);

        IncidentEvidenceEntity saved = evidenceRepository.save(evidence);

        IncidentContextEntity context = getContext(incidentId);

        ArrayNode evidenceIds = asArray(context.getEvidenceIds());
        evidenceIds.add(saved.getId().toString());

        context.setEvidenceIds(evidenceIds);
        contextRepository.save(context);

        ObjectNode details = JsonNodeFactory.instance.objectNode();
        details.put("evidenceId", saved.getId().toString());
        details.put("sourceTool", sourceTool);
        details.put("truncated", truncated);

        recordEvent(incidentId, "EVIDENCE_RECORDED", details);

        return saved.getId();
    }

    /**
     * Updates the rolling summary and bounded recent conversation.
     * The full audit trail remains in incident_events.
     */
    @Transactional
    public void updateContext(
            UUID incidentId,
            String rollingSummary,
            ArrayNode recentMessages,
            ArrayNode currentHypotheses,
            int stepCount) {

        requireIncident(incidentId);

        if (stepCount < 0) {
            throw new IllegalArgumentException(
                    "stepCount cannot be negative");
        }

        IncidentContextEntity context = getContext(incidentId);

        ArrayNode boundedMessages = recentMessages == null
                ? JsonNodeFactory.instance.arrayNode()
                : recentMessages.deepCopy();

        while (boundedMessages.size() > 8) {
            boundedMessages.remove(0);
        }

        context.setRollingSummary(
                rollingSummary == null ? "" : rollingSummary);
        context.setRecentMessages(boundedMessages);
        context.setCurrentHypotheses(
                currentHypotheses == null
                        ? JsonNodeFactory.instance.arrayNode()
                        : currentHypotheses.deepCopy()
        );
        context.setStepCount(stepCount);

        contextRepository.save(context);

        ObjectNode details = JsonNodeFactory.instance.objectNode();
        details.put("stepCount", stepCount);

        recordEvent(incidentId, "CONTEXT_UPDATED", details);
    }

    /**
     * Changes the incident state and records the transition.
     */
    
        @Transactional
        public void changeState(UUID incidentId, String newState) {

                if (newState == null || newState.isBlank()) {
                        throw new IllegalArgumentException("newState is required");
                }

                IncidentEntity incident = requireIncident(incidentId);

                IncidentState current;
                IncidentState next;

                try {
                        current = IncidentState.valueOf(incident.getState());
                        next = IncidentState.valueOf(newState);
                } catch (IllegalArgumentException ex) {
                        throw new IllegalArgumentException(
                                "Unknown incident state: " + newState, ex);
                }

                if (!current.canTransitionTo(next)) {
                        throw new IllegalStateException(
                                "Invalid incident state transition: "
                                        + current + " -> " + next);
                }

                incident.setState(next.name());
                incidentRepository.save(incident);

                ObjectNode details = JsonNodeFactory.instance.objectNode();
                details.put("from", current.name());
                details.put("to", next.name());

                recordEvent(incidentId, "STATE_CHANGED", details);
        }


    /**
     * Loads the persistent context after a restart or between steps.
     */
    @Transactional(readOnly = true)
    public IncidentContextEntity loadContext(UUID incidentId) {
        requireIncident(incidentId);
        return getContext(incidentId);
    }

    @Transactional(readOnly = true)
    public List<IncidentEventEntity> getTimeline(UUID incidentId) {
        requireIncident(incidentId);
        return eventRepository
                .findAllByIncidentIdOrderByCreatedAtAsc(incidentId);
    }

    @Transactional(readOnly = true)
    public List<IncidentEvidenceEntity> getEvidence(UUID incidentId) {
        requireIncident(incidentId);
        return evidenceRepository
                .findAllByIncidentIdOrderByCreatedAtAsc(incidentId);
    }

    private IncidentEntity requireIncident(UUID incidentId) {
        return incidentRepository.findById(incidentId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Incident not found: " + incidentId));
    }

    private IncidentContextEntity getContext(UUID incidentId) {
        return contextRepository.findById(incidentId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Incident context not found: " + incidentId));
    }

    private ArrayNode asArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return objectMapper.createArrayNode();
        }
        return (ArrayNode) node.deepCopy();
    }
    
        private void acquireDeduplicationLock(
                String targetId,
                String dedupKey) {

        String lockKey = targetId + ":" + dedupKey;

        jdbcTemplate.execute(
                (ConnectionCallback<Void>) connection -> {
                        try (PreparedStatement statement =
                                connection.prepareStatement(
                                        "SELECT pg_advisory_xact_lock(" +
                                        "hashtextextended(?, 0))")) {

                        statement.setString(1, lockKey);

                        try (ResultSet resultSet =
                                        statement.executeQuery()) {
                                resultSet.next();
                        }
                        }

                        return null;
                }
        );
        }

       @Transactional
        public boolean beginRecoveryVerificationIfEligible(UUID incidentId) {
        IncidentEntity incident = requireIncident(incidentId);

        // Serialize concurrent attempts to enqueue verification for this incident.
        jdbcTemplate.queryForObject(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                Object.class,
                "incident-recovery:" + incidentId
        );

        // Re-read after acquiring the lock.
        incident = requireIncident(incidentId);

        IncidentState current =
                IncidentState.valueOf(incident.getState());

        if (current != IncidentState.DIAGNOSED
                && current != IncidentState.INCONCLUSIVE
                && current != IncidentState.VERIFYING) {
                return false;
        }

        String alertName = extractAlertName(incident.getAlertPayload());

        RecoveryPolicy policy =
                recoveryPolicyProperties.getPolicy(alertName);

        if (policy == null) {
                ObjectNode details = JsonNodeFactory.instance.objectNode();
                details.put("alertName", alertName);
                details.put("reason", "No recovery policy configured");

                recordEvent(
                        incidentId,
                        "RECOVERY_POLICY_MISSING",
                        details
                );

                return false;
        }

        final String policyJson;

        try {
                policyJson = objectMapper.writeValueAsString(policy);
        } catch (Exception exception) {
                throw new IllegalStateException(
                        "Could not serialize recovery policy for alert " + alertName,
                        exception
                );
        }

        int inserted = jdbcTemplate.update("""
                INSERT INTO incident_recovery_verification_jobs
                        (incident_id, status, recovery_policy)
                VALUES (?, 'PENDING', CAST(? AS JSONB))
                ON CONFLICT (incident_id) DO NOTHING
                """,
                incidentId,
                policyJson
        );

        if (current != IncidentState.VERIFYING) {
                changeState(incidentId, IncidentState.VERIFYING.name());
        }

        if (inserted == 1) {
                ObjectNode details = JsonNodeFactory.instance.objectNode();
                details.put("status", "PENDING");
                details.put("alertName", alertName);
                details.set(
                        "recoveryPolicy",
                        objectMapper.valueToTree(policy)
                );

                recordEvent(
                        incidentId,
                        "RECOVERY_VERIFICATION_ENQUEUED",
                        details
                );
        }

        return true;
        }

        private String extractAlertName(JsonNode alertPayload) {
        if (alertPayload == null || !alertPayload.isObject()) {
                throw new IllegalArgumentException(
                        "Incident alert payload is missing or invalid"
                );
        }

        JsonNode alerts = alertPayload.get("alerts");

        if (alerts == null || !alerts.isArray() || alerts.isEmpty()) {
                throw new IllegalArgumentException(
                        "Alertmanager payload does not contain alerts"
                );
        }

        String alertName = null;

        for (JsonNode alert : alerts) {
                JsonNode labels = alert.get("labels");
                JsonNode nameNode =
                        labels == null ? null : labels.get("alertname");

                if (nameNode == null || !nameNode.isTextual()
                        || nameNode.asText().isBlank()) {
                throw new IllegalArgumentException(
                        "Alertmanager alert is missing labels.alertname"
                );
                }

                String currentName = nameNode.asText();

                if (alertName == null) {
                alertName = currentName;
                } else if (!alertName.equals(currentName)) {
                throw new IllegalArgumentException(
                        "Incident payload contains multiple alert names"
                );
                }
        }

        return alertName;
        }

        @Transactional
        public boolean returnToInvestigationAfterRecoveryFailure(UUID incidentId) {
        IncidentEntity incident = requireIncident(incidentId);

        // Serialize recovery failure handling for this incident.
        jdbcTemplate.queryForObject(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                Object.class,
                "incident-recovery:" + incidentId
        );

        // Re-read after acquiring the lock.
        incident = requireIncident(incidentId);

        IncidentState current = IncidentState.valueOf(incident.getState());

        if (current != IncidentState.VERIFYING) {
                return false;
        }

        // Change state first. If enqueueing fails, the transaction rolls back.
        changeState(incidentId, IncidentState.INVESTIGATING.name());

        int inserted = jdbcTemplate.update("""
                INSERT INTO incident_investigation_jobs
                        (incident_id, status, attempts, available_at)
                VALUES (?, 'PENDING', 0, NOW())
                ON CONFLICT (incident_id) DO UPDATE
                SET status = 'PENDING',
                        attempts = 0,
                        available_at = NOW(),
                        locked_at = NULL,
                        last_error = NULL,
                        updated_at = NOW()
                """,
                incidentId
        );

        ObjectNode details = JsonNodeFactory.instance.objectNode();
        details.put("reason", "Recovery verification found persistent unhealthy state");
        details.put("investigationJobEnqueued", inserted == 1);

        recordEvent(
                incidentId,
                "RECOVERY_FAILED_REINVESTIGATION_ENQUEUED",
                details
        );

        return true;
        }

        @Transactional
public RecoveryApplyResult applyRecoveryEvaluation(
        UUID incidentId,
        Timestamp lockedAt,
        RecoveryEvaluation evaluation,
        RecoveryPolicy policy
) {
    // Only the worker holding this exact lease may update the counters.
    List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
        UPDATE incident_recovery_verification_jobs
        SET
            consecutive_healthy_checks =
                CASE WHEN ? = 'HEALTHY'
                     THEN consecutive_healthy_checks + 1 ELSE 0 END,
            consecutive_unhealthy_checks =
                CASE WHEN ? = 'UNHEALTHY'
                     THEN consecutive_unhealthy_checks + 1 ELSE 0 END,
            consecutive_unknown_checks =
                CASE WHEN ? = 'UNKNOWN'
                     THEN consecutive_unknown_checks + 1 ELSE 0 END,
            updated_at = NOW()
        WHERE incident_id = ?
          AND status = 'RUNNING'
          AND locked_at = ?
        RETURNING
            consecutive_healthy_checks,
            consecutive_unhealthy_checks,
            consecutive_unknown_checks
        """,
        evaluation.status().name(),
        evaluation.status().name(),
        evaluation.status().name(),
        incidentId,
        lockedAt
    );

    if (rows.isEmpty()) {
        return RecoveryApplyResult.LEASE_LOST;
    }

    Map<String, Object> counters = rows.getFirst();

    int healthy = ((Number) counters.get("consecutive_healthy_checks")).intValue();
    int unhealthy = ((Number) counters.get("consecutive_unhealthy_checks")).intValue();
    int unknown = ((Number) counters.get("consecutive_unknown_checks")).intValue();

    String incidentState = jdbcTemplate.queryForObject(
            "SELECT state FROM incidents WHERE id = ?",
            String.class,
            incidentId
    );

    if (!"VERIFYING".equals(incidentState)) {
        return RecoveryApplyResult.INCIDENT_NOT_VERIFYING;
    }

    boolean shouldResolve =
            evaluation.status() == RecoveryStatus.HEALTHY
            && healthy >= policy.consecutiveHealthyChecks();

    boolean shouldReinvestigate =
            evaluation.status() == RecoveryStatus.UNHEALTHY
            && unhealthy >= 5;

    boolean shouldMarkInconclusive =
            evaluation.status() == RecoveryStatus.UNKNOWN
            && unknown >= 3;

    if (shouldResolve) {
        changeState(incidentId, "RESOLVED");
        completeRecoveryJob(incidentId, lockedAt);
        recordEvent(incidentId, "RECOVERY_VERIFIED", objectMapper.createObjectNode()
                .put("reason", evaluation.reason())
                .put("consecutiveHealthyChecks", healthy));

    } else if (shouldReinvestigate) {
        // Existing method changes VERIFYING -> INVESTIGATING and enqueues
        // a new investigation job. Both operations join this transaction.
        returnToInvestigationAfterRecoveryFailure(incidentId);
        completeRecoveryJob(incidentId, lockedAt);
        recordEvent(incidentId, "RECOVERY_FAILED_REINVESTIGATION_ENQUEUED",
                objectMapper.createObjectNode()
                        .put("reason", evaluation.reason())
                        .put("consecutiveUnhealthyChecks", unhealthy));

    } else if (shouldMarkInconclusive) {
        changeState(incidentId, "INCONCLUSIVE");
        completeRecoveryJob(incidentId, lockedAt);
        recordEvent(incidentId, "RECOVERY_VERIFICATION_INCONCLUSIVE",
                objectMapper.createObjectNode()
                        .put("reason", evaluation.reason())
                        .put("consecutiveUnknownChecks", unknown));

    } else {
        // Schedule the next deterministic check.
        jdbcTemplate.update("""
            UPDATE incident_recovery_verification_jobs
            SET
                status = 'PENDING',
                available_at = NOW() + (? * INTERVAL '1 second'),
                locked_at = NULL,
                updated_at = NOW()
            WHERE incident_id = ?
              AND status = 'RUNNING'
              AND locked_at = ?
            """,
            policy.intervalSeconds(),
            incidentId,
            lockedAt
        );
    }

    return RecoveryApplyResult.APPLIED;
}

        private void completeRecoveryJob(UUID incidentId, Timestamp lockedAt) {
        int updated = jdbcTemplate.update("""
                UPDATE incident_recovery_verification_jobs
                SET
                status = 'COMPLETED',
                locked_at = NULL,
                updated_at = NOW()
                WHERE incident_id = ?
                AND status = 'RUNNING'
                AND locked_at = ?
                """,
                incidentId,
                lockedAt
        );

        if (updated != 1) {
                throw new IllegalStateException(
                        "Recovery verification lease was lost while completing job: " + incidentId
                );
        }
        }
}
