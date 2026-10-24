
package com.agents_service.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agents_service.incident.entity.IncidentAlertEntity;
import com.agents_service.incident.repository.IncidentAlertRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@Service
public class IncidentAlertService {

    private static final String FIRING = "firing";
    private static final String RESOLVED = "resolved";

    private final IncidentAlertRepository alertRepository;
    private final IncidentService incidentService;
    private final JdbcTemplate jdbcTemplate;

    public IncidentAlertService(
            IncidentAlertRepository alertRepository,
            IncidentService incidentService,
            JdbcTemplate jdbcTemplate) {
        this.alertRepository = alertRepository;
        this.incidentService = incidentService;
        this.jdbcTemplate = jdbcTemplate;
    }

        
        @Transactional
        public IncidentAlertEntity recordFiring(
                UUID incidentId,
                String targetId,
                String fingerprint,
                String alertName,
                JsonNode alertPayload) {

        validate(targetId, fingerprint, alertName);

        // Always acquire locks in this order:
        // 1. Incident lock
        // 2. Alert fingerprint lock
        acquireIncidentLock(incidentId);
        acquireAlertLock(targetId, fingerprint);

        Optional<IncidentAlertEntity> existing =
                alertRepository
                        .findFirstByTargetIdAndFingerprintAndAlertStatusOrderByLastSeenAtDesc(
                                targetId, fingerprint, FIRING);

        Instant now = Instant.now();

        if (existing.isPresent()) {
                IncidentAlertEntity alert = existing.get();

                if (!alert.getIncidentId().equals(incidentId)) {
                throw new IllegalStateException(
                        "This alert fingerprint is already associated "
                                + "with a different active incident: "
                                + alert.getIncidentId());
                }

                // Repeated firing delivery: update the existing record.
                alert.setLastSeenAt(now);
                alert.setAlertPayload(copyPayload(alertPayload));

                return alertRepository.save(alert);
        }

        IncidentAlertEntity alert = new IncidentAlertEntity();
        alert.setIncidentId(incidentId);
        alert.setTargetId(targetId);
        alert.setFingerprint(fingerprint);
        alert.setAlertName(alertName);
        alert.setAlertStatus(FIRING);
        alert.setAlertPayload(copyPayload(alertPayload));
        alert.setFirstSeenAt(now);
        alert.setLastSeenAt(now);

        IncidentAlertEntity saved = alertRepository.saveAndFlush(alert);

        ObjectNode details = JsonNodeFactory.instance.objectNode();
        details.put("fingerprint", fingerprint);
        details.put("alertname", alertName);
        details.put("targetId", targetId);
        details.put("status", FIRING);

        incidentService.recordEvent(
                incidentId,
                "ALERT_INSTANCE_FIRING",
                details);

        return saved;
        }



        @Transactional
        public Optional<UUID> recordResolved(
                String targetId,
                String fingerprint,
                String alertName,
                JsonNode alertPayload) {

        validate(targetId, fingerprint, alertName);

        // Initial lookup only discovers which incident to lock.
        // The result must be rechecked after both locks are acquired.
        Optional<IncidentAlertEntity> initial =
                alertRepository
                        .findFirstByTargetIdAndFingerprintAndAlertStatusOrderByLastSeenAtDesc(
                                targetId, fingerprint, FIRING);

        if (initial.isEmpty()) {
                return Optional.empty();
        }

        UUID incidentId = initial.get().getIncidentId();

        // Use the same lock order as recordFiring().
        acquireIncidentLock(incidentId);
        acquireAlertLock(targetId, fingerprint);

        // Re-read after locking: the initial lookup may now be stale.
        Optional<IncidentAlertEntity> existing =
                alertRepository
                        .findFirstByTargetIdAndFingerprintAndAlertStatusOrderByLastSeenAtDesc(
                                targetId, fingerprint, FIRING);

        if (existing.isEmpty()) {
                return Optional.empty();
        }

        IncidentAlertEntity alert = existing.get();

        // Defensive check in case the active record changed incidents.
        if (!alert.getIncidentId().equals(incidentId)) {
                throw new IllegalStateException(
                        "Alert fingerprint changed incidents while resolving: "
                                + fingerprint);
        }

        Instant now = Instant.now();

        alert.setAlertStatus(RESOLVED);
        alert.setResolvedAt(now);
        alert.setLastSeenAt(now);
        alert.setAlertPayload(copyPayload(alertPayload));

        alertRepository.saveAndFlush(alert);

        ObjectNode details = JsonNodeFactory.instance.objectNode();
        details.put("fingerprint", fingerprint);
        details.put("alertname", alertName);
        details.put("targetId", targetId);
        details.put("status", RESOLVED);

        incidentService.recordEvent(
                incidentId,
                "ALERT_INSTANCE_RESOLVED",
                details);

        // Check every alert instance belonging to this incident.
        boolean hasFiringAlerts =
                alertRepository.existsByIncidentIdAndAlertStatus(
                        incidentId, FIRING);

        if (!hasFiringAlerts) {
                boolean verificationStarted =
                        incidentService
                                .beginRecoveryVerificationIfDiagnosed(incidentId);

                if (verificationStarted) {
                ObjectNode verificationDetails =
                        JsonNodeFactory.instance.objectNode();

                verificationDetails.put(
                        "reason",
                        "No firing alert instances remain");

                incidentService.recordEvent(
                        incidentId,
                        "RECOVERY_VERIFICATION_REQUIRED",
                        verificationDetails);
                }
        }

        return Optional.of(incidentId);
        }


    private void acquireAlertLock(
            String targetId,
            String fingerprint) {

        String lockKey = "incident-alert:" + targetId + ":" + fingerprint;

        jdbcTemplate.execute(
                (ConnectionCallback<Void>) connection -> {
                    try (var statement = connection.prepareStatement(
                            "SELECT pg_advisory_xact_lock("
                                    + "hashtextextended(?, 0))")) {
                        statement.setString(1, lockKey);
                        statement.execute();
                    }
                    return null;
                });
    }

    private void validate(
            String targetId,
            String fingerprint,
            String alertName) {

        if (targetId == null || targetId.isBlank()
                || fingerprint == null || fingerprint.isBlank()
                || alertName == null || alertName.isBlank()) {
            throw new IllegalArgumentException(
                    "targetId, fingerprint and alertName are required");
        }
    }

    private JsonNode copyPayload(JsonNode payload) {
        return payload == null
                ? JsonNodeFactory.instance.objectNode()
                : payload.deepCopy();
    }

    private void acquireIncidentLock(UUID incidentId) {
    jdbcTemplate.execute(
            (ConnectionCallback<Void>) connection -> {
                try (var statement = connection.prepareStatement(
                        "SELECT pg_advisory_xact_lock("
                                + "hashtextextended(?, 0))")) {
                    statement.setString(
                            1, "incident-recovery:" + incidentId);
                    statement.execute();
                }
                return null;
            });
}
}
