
package com.agents_service.tool;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.agents_service.service.IncidentExecutionContext;
import com.agents_service.service.IncidentService;

import com.agents_service.client.ToolsServiceClient;
import com.agents_service.tool.dto.ToolRequest;
import com.agents_service.tool.dto.ToolResponse;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@Component
public class AgentTools {

    private static final int MAX_EVIDENCE_CHARS = 20_000;

    private final ToolsServiceClient toolsServiceClient;
    private final IncidentExecutionContext executionContext;
    private final IncidentService incidentService;
    private final String activeTargetId;

    public AgentTools(
            ToolsServiceClient toolsServiceClient,
            IncidentExecutionContext executionContext,
            IncidentService incidentService,
            @Value("${agent.active-target-id}") String activeTargetId) {

        this.toolsServiceClient = toolsServiceClient;
        this.executionContext = executionContext;
        this.incidentService = incidentService;
        this.activeTargetId = activeTargetId;
    }

    @Tool(description = "List Kubernetes pods in the configured target. Use this to inspect pod names, status, and restart information when investigating an incident.")
    public String getPods() {
        return execute("getPods", Map.of());
    }

    @Tool(description = "Retrieve recent logs for a Kubernetes pod. Use getPods first to identify a valid pod name.")
    public String getPodLogs(
            @ToolParam(description = "Exact name of the Kubernetes pod")
            String podName,
            @ToolParam(description = "Number of recent log lines to retrieve, between 1 and 200")
            int tailLines) {

        if (podName == null || podName.isBlank()) {
            return "Tool error: podName must not be blank.";
        }

        if (tailLines < 1 || tailLines > 200) {
            return "Tool error: tailLines must be between 1 and 200.";
        }

        return execute(
                "getPodLogs",
                Map.of("podName", podName, "tailLines", tailLines));
    }

    @Tool(description = """
    Query Prometheus using a valid PromQL expression.
    Use metric names and label selectors supported by the target.
    A bare label selector such as {job="example"} is not a complete
    PromQL expression in this setup; use a metric selector such as
    up{job="example"} instead.
    """)
    public String queryPrometheus(
            @ToolParam(description = "PromQL expression to execute")
            String query) {

        if (query == null || query.isBlank()) {
            return "Tool error: query must not be blank.";
        }

        return execute("queryPrometheus", Map.of("query", query));
    }

    private String execute(
            String toolName,
            Map<String, Object> arguments) {

        ToolRequest request = new ToolRequest();
        request.setTargetId(activeTargetId);
        request.setTool(toolName);
        request.setArguments(arguments);

        ToolResponse response;

        try {
            response = toolsServiceClient.executeTool(request);
        } catch (Exception exception) {
            String error = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();

            recordToolEvidence(
                    toolName, arguments, false, null, error);

            return "Tool error: " + error;
        }

        if (response == null) {
            String error = "tools-service returned an empty response.";

            recordToolEvidence(
                    toolName, arguments, false, null, error);

            return "Tool error: " + error;
        }

        if (!response.isSuccess()) {
            String error = response.getError() == null
                    ? "Unknown tools-service error."
                    : response.getError();

            recordToolEvidence(
                    toolName, arguments, false, null, error);

            return "Tool error: " + error;
        }

        String result = response.getResponse();

        recordToolEvidence(
                toolName, arguments, true, result, null);

        return result;
    }

    private void recordToolEvidence(
            String toolName,
            Map<String, Object> arguments,
            boolean success,
            String response,
            String error) {

        UUID incidentId = executionContext.currentIncidentId();

        // Standalone tool-calling tests have no associated incident.
        if (incidentId == null) {
            return;
        }

        ObjectNode findings = JsonNodeFactory.instance.objectNode();

        findings.put("tool", toolName);
        findings.put("targetId", activeTargetId);
        findings.put("success", success);
        findings.put("arguments", arguments.toString());

        boolean truncated = false;

        if (response != null) {
            truncated = response.length() > MAX_EVIDENCE_CHARS;

            findings.put(
                    "response",
                    truncated
                            ? response.substring(0, MAX_EVIDENCE_CHARS)
                            : response);
        }

        if (error != null) {
            findings.put("error", error);
        }

        incidentService.recordEvidence(
                incidentId,
                toolName,
                "Tool call " + toolName
                        + (success ? " succeeded" : " failed"),
                findings,
                truncated,
                null);
    }
}
