
package com.agents_service.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agents_service.service.IncidentOrchestrator;

@RestController
@RequestMapping("/test/incidents")
public class IncidentInvestigationTestController {

    private final IncidentOrchestrator orchestrator;

    public IncidentInvestigationTestController(
            IncidentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/{incidentId}/investigate")
    public ResponseEntity<Map<String, String>> investigate(
            @PathVariable UUID incidentId) {

        orchestrator.investigate(incidentId);

        return ResponseEntity.ok(Map.of(
                "incidentId", incidentId.toString(),
                "status", "investigation_completed"
        ));
    }
}
