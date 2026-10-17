
package com.agents_service.tool;

import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.agents_service.client.ToolsServiceClient;
import com.agents_service.tool.dto.ToolRequest;
import com.agents_service.tool.dto.ToolResponse;

@Component
public class AgentTools {

    private final ToolsServiceClient toolsServiceClient;
    private final String activeTargetId;


    public AgentTools(ToolsServiceClient toolsServiceClient,
        @Value("${agent.active-target-id}") String activeTargetId
    ) {
        this.toolsServiceClient = toolsServiceClient;
        this.activeTargetId=activeTargetId;
    }

    @Tool(description = "List Kubernetes pods in the configured target. Use this to inspect pod names, status, and restart information when investigating an incident.")
    public String getPods() {
        return execute("getPods", Map.of());
    }

    @Tool(description = "Retrieve recent logs for a Kubernetes pod. Use getPods first to identify a valid pod name.")
    public String getPodLogs(
            @ToolParam(description = "Exact name of the Kubernetes pod") String podName,
            @ToolParam(description = "Number of recent log lines to retrieve, between 1 and 200") int tailLines) {

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

    @Tool(description = "Query Prometheus using a PromQL expression to investigate metrics related to an incident.")
    public String queryPrometheus(
            @ToolParam(description = "PromQL expression to execute") String query) {

        if (query == null || query.isBlank()) {
            return "Tool error: query must not be blank.";
        }

        return execute("queryPrometheus", Map.of("query", query));
    }

    private String execute(String toolName, Map<String, Object> arguments) {
        try {
            ToolRequest request = new ToolRequest();

            // The target is controlled by application configuration,
            // never selected by the LLM.
            request.setTargetId(activeTargetId);
            request.setTool(toolName);
            request.setArguments(arguments);

            ToolResponse response = toolsServiceClient.executeTool(request);

            if (response == null) {
                return "Tool error: tools-service returned an empty response.";
            }

            if (!response.isSuccess()) {
                return "Tool error: " + response.getError();
            }

            return response.getResponse();

        } catch (Exception e) {
            return "Tool error: " + e.getMessage();
        }
    }
}
