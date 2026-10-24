
package com.agents_service.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.agents_service.service.IncidentAlertService;
import com.agents_service.service.IncidentService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@RestController
public class AlertmanagerWebhookController {

    private final IncidentService incidentService;
    private final IncidentAlertService incidentAlertService;
    private final String activeTargetId;

    public AlertmanagerWebhookController(
            IncidentService incidentService,
            IncidentAlertService incidentAlertService,
            @Value("${agent.active-target-id}") String activeTargetId) {
        this.incidentService = incidentService;
        this.incidentAlertService = incidentAlertService;
        this.activeTargetId = activeTargetId;
    }

    @PostMapping("/webhooks/alertmanager")
    public ResponseEntity<Map<String, Object>> receiveAlert(
            @RequestBody JsonNode payload) {

        JsonNode alerts = payload.path("alerts");

        if (!alerts.isArray()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error",
                            "Expected an Alertmanager payload with an alerts array"));
        }

        // Validate the complete payload before writing anything.
        for (JsonNode alert : alerts) {
            String status = alert.path("status").asString("");
            if (!"firing".equals(status) && !"resolved".equals(status)) {
                continue;
            }

            JsonNode labels = alert.path("labels");
            if (!labels.isObject()
                    || labels.path("alertname").asString("").isBlank()) {
                return ResponseEntity.badRequest().body(
                        Map.of("error",
                                "Every firing/resolved alert must have labels.alertname"));
            }
        }

        int processedFiring = 0;
        int processedResolved = 0;
        int ignored = 0;
        int unmatchedResolved = 0;

        for (JsonNode alert : alerts) {
            String status = alert.path("status").asString("");
            JsonNode labels = alert.path("labels");

            if (!"firing".equals(status) && !"resolved".equals(status)) {
                ignored++;
                continue;
            }

            String alertName = labels.path("alertname").asString("");
            String namespace = firstNonBlank(
                    labels.path("namespace").asString(""),
                    "unknown-namespace");

            String workload = firstNonBlank(
                    labels.path("deployment").asString(""),
                    labels.path("statefulset").asString(""),
                    labels.path("daemonset").asString(""),
                    labels.path("app").asString(""),
                    labels.path("service").asString(""),
                    labels.path("pod").asString(""),
                    labels.path("job").asString(""),
                    alertName);

            String dedupKey = String.join(
                    "/",
                    activeTargetId.trim().toLowerCase(Locale.ROOT),
                    namespace.trim().toLowerCase(Locale.ROOT),
                    workload.trim().toLowerCase(Locale.ROOT));

            String fingerprint = alert.path("fingerprint").asString("");

            if (fingerprint.isBlank()) {
                // Without a fingerprint, safe per-alert lifecycle matching
                // is not possible.
                ignored++;
                continue;
            }

            if ("firing".equals(status)) {
                var result = incidentService.openOrGetIncidentResult(
                        activeTargetId,
                        dedupKey,
                        fingerprint,
                        alert);

                var incident = result.incident();

                incidentAlertService.recordFiring(
                        incident.getId(),
                        activeTargetId,
                        fingerprint,
                        alertName,
                        alert);

                ObjectNode details = JsonNodeFactory.instance.objectNode();
                details.put("alertname", alertName);
                details.put("namespace", namespace);
                details.put("fingerprint", fingerprint);
                details.put("status", "firing");

                incidentService.recordEvent(
                        incident.getId(),
                        "ALERT_RECEIVED",
                        details);

                processedFiring++;
            } else {
                var resolvedIncident = incidentAlertService.recordResolved(
                        activeTargetId,
                        fingerprint,
                        alertName,
                        alert);

                if (resolvedIncident.isPresent()) {
                    processedResolved++;
                } else {
                    unmatchedResolved++;
                }
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("received", alerts.size());
        response.put("processedFiringAlerts", processedFiring);
        response.put("processedResolvedAlerts", processedResolved);
        response.put("unmatchedResolvedAlerts", unmatchedResolved);
        response.put("ignoredAlerts", ignored);

        return ResponseEntity.ok(response);
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "unknown";
    }
}
