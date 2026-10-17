
package com.agents_service.controller;

 
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.agents_service.service.IncidentService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.Map;
 

@RestController
public class AlertmanagerWebhookController {

    private final IncidentService incidentService;
    private final String activeTargetId;

    public AlertmanagerWebhookController(
            IncidentService incidentService,
            @Value("${agent.active-target-id}") String activeTargetId) {
        this.incidentService = incidentService;
        this.activeTargetId = activeTargetId;
    }

    @PostMapping("/webhooks/alertmanager")
    public ResponseEntity<Map<String, Object>> receiveAlert(
            @RequestBody JsonNode payload) {

        JsonNode alerts = payload.path("alerts");

        if (!alerts.isArray()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Expected an Alertmanager payload with an alerts array"));
        }

        // Validate all firing alerts before writing anything.
        for (JsonNode alert : alerts) {
            if (!"firing".equals(alert.path("status").asString())) {
                continue;
            }

            JsonNode labels = alert.path("labels");
            String alertName = labels.path("alertname").asString("");

            if (!labels.isObject() || alertName.isBlank()) {
                return ResponseEntity.badRequest().body(
                        Map.of("error",
                                "Every firing alert must have labels.alertname"));
            }
        }

        int processed = 0;
        int ignored = 0;

        for (JsonNode alert : alerts) {
            String status = alert.path("status").asString("");

            // Resolved notifications must not create new incidents.
            // Incident closure will be handled by the verification workflow.
            if (!"firing".equals(status)) {
                ignored++;
                continue;
            }

            JsonNode labels = alert.path("labels");

            String alertName = labels.path("alertname").asString("");
            String service = firstNonBlank(
                    labels.path("service").asString(""),
                    labels.path("job").asString(""),
                    labels.path("app").asString(""),
                    "unknown-service"
            );
            String namespace = firstNonBlank(
                    labels.path("namespace").asString(""),
                    "default"
            );

            String dedupKey =
                    alertName + ":" + service + ":" + namespace;

            String fingerprint =
                    alert.path("fingerprint").asString("");

            var incident = incidentService.openOrGetIncident(
                    activeTargetId,
                    dedupKey,
                    fingerprint.isBlank() ? null : fingerprint,
                    alert
            );

            ObjectNode details =
                    JsonNodeFactory.instance.objectNode();

            details.put("alertname", alertName);
            details.put("service", service);
            details.put("namespace", namespace);

            if (!fingerprint.isBlank()) {
                details.put("fingerprint", fingerprint);
            }

            incidentService.recordEvent(
                    incident.getId(),
                    "ALERT_RECEIVED",
                    details
            );

            processed++;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("received", alerts.size());
        response.put("processedFiringAlerts", processed);
        response.put("ignoredNonFiringAlerts", ignored);

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
    private String textOrEmpty(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return "";
        }

        return node.asString("");
    }   
}
