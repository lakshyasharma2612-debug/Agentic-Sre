
package com.agents_service;

import com.agents_service.incident.entity.IncidentEntity;
import com.agents_service.service.IncidentService;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class IncidentServiceIntegrationTest {

    @Autowired
    private IncidentService incidentService;

    @Test
    void shouldPersistIncidentContextEvidenceAndEvents() {
        // 1. Create a test alert payload.
        ObjectNode alert = JsonNodeFactory.instance.objectNode();
        alert.put("alertname", "TestHighErrorRate");
        alert.put("service", "event-platform");
        alert.put("namespace", "event-platform");

        // 2. Create the incident.
        IncidentEntity first = incidentService.openOrGetIncident(
                "event-platform",
                "TestHighErrorRate:event-platform:event-platform",
                "test-fingerprint-" + UUID.randomUUID(),
                alert
        );

        assertNotNull(first.getId());
        assertEquals("INVESTIGATING", first.getState());

        // 3. Verify the context was created.
        var context = incidentService.loadContext(first.getId());

        assertEquals(first.getId(), context.getIncidentId());
        assertEquals(0, context.getStepCount());
        assertTrue(context.getRecentMessages().isArray());

        // 4. Submit the same active incident key again.
        IncidentEntity duplicate = incidentService.openOrGetIncident(
                "event-platform",
                "TestHighErrorRate:event-platform:event-platform",
                "another-test-fingerprint",
                alert
        );

        assertEquals(
                first.getId(),
                duplicate.getId(),
                "A duplicate alert should reuse the active incident"
        );

        // 5. Store investigation evidence.
        ObjectNode findings = JsonNodeFactory.instance.objectNode();
        findings.put("healthyPods", 2);
        findings.put("unhealthyPods", 1);

        UUID evidenceId = incidentService.recordEvidence(
                first.getId(),
                "getPods",
                "One unhealthy pod was found",
                findings,
                false,
                null
        );

        assertNotNull(evidenceId);
        assertEquals(
                1,
                incidentService.getEvidence(first.getId()).size()
        );

        // 6. Update the conversation context.
        ArrayNode messages = JsonNodeFactory.instance.arrayNode();
        ObjectNode message = JsonNodeFactory.instance.objectNode();
        message.put("role", "assistant");
        message.put("content", "Investigating unhealthy pod");
        messages.add(message);

        ArrayNode hypotheses = JsonNodeFactory.instance.arrayNode();
        hypotheses.add("Possible application crash");

        incidentService.updateContext(
                first.getId(),
                "One pod appears unhealthy.",
                messages,
                hypotheses,
                1
        );

        var updatedContext =
                incidentService.loadContext(first.getId());

        assertEquals(1, updatedContext.getStepCount());
        assertEquals(1, updatedContext.getRecentMessages().size());
        assertEquals(
                "One pod appears unhealthy.",
                updatedContext.getRollingSummary()
        );

        // 7. Verify the audit timeline.
        var events = incidentService.getTimeline(first.getId());

        assertTrue(
                events.stream().anyMatch(
                        event -> event.getEventType()
                                .equals("INCIDENT_CREATED"))
        );

        assertTrue(
                events.stream().anyMatch(
                        event -> event.getEventType()
                                .equals("DUPLICATE_ALERT_RECEIVED"))
        );

        assertTrue(
                events.stream().anyMatch(
                        event -> event.getEventType()
                                .equals("EVIDENCE_RECORDED"))
        );

        assertTrue(
                events.stream().anyMatch(
                        event -> event.getEventType()
                                .equals("CONTEXT_UPDATED"))
        );
    }
}
