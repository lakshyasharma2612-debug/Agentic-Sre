package com.agents_service.recovery;

import com.agents_service.client.ToolsServiceClient;
import com.agents_service.tool.dto.ToolRequest;
import com.agents_service.tool.dto.ToolResponse;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class PodReadyRecoveryEvaluator implements RecoveryEvaluator {

    private final ToolsServiceClient toolsServiceClient;
    private final ObjectMapper objectMapper;

    public PodReadyRecoveryEvaluator(
            ToolsServiceClient toolsServiceClient,
            ObjectMapper objectMapper
    ) {
        this.toolsServiceClient = toolsServiceClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String supportedType() {
        return "POD_READY";
    }

    @Override
    public RecoveryEvaluation evaluate(RecoveryPolicy policy) {
        Map<String, String> parameters = policy.parameters();

        String targetId = parameters.get("targetId");
        String namespace = parameters.get("namespace");
        String podName = parameters.get("podName");

        if (isBlank(targetId) || isBlank(namespace) || isBlank(podName)) {
            return RecoveryEvaluation.unknown(
                    "POD_READY requires targetId, namespace, and podName"
            );
        }

        try {
            ToolRequest request = new ToolRequest();

            request.setTargetId(targetId);
            request.setTool("getPodReadiness");

            Map<String, Object> arguments = new HashMap<>();
            arguments.put("namespace", namespace);
            arguments.put("podName", podName);

            request.setArguments(arguments);

            ToolResponse response = toolsServiceClient.executeTool(request);

            if (response == null) {
                return RecoveryEvaluation.unknown(
                        "Tools-service returned a null response"
                );
            }

            if (!response.isSuccess()) {
                return RecoveryEvaluation.unknown(
                        "Tools-service execution failed: " + response.getError()
                );
            }

            if (response.getResponse() == null
                    || response.getResponse().isBlank()) {
                return RecoveryEvaluation.unknown(
                        "Tools-service returned an empty response body"
                );
            }

            JsonNode readiness =
                    objectMapper.readTree(response.getResponse());

            JsonNode readyNode = readiness.get("ready");

            if (readyNode == null || !readyNode.isBoolean()) {
                return RecoveryEvaluation.unknown(
                        "Readiness response is missing a valid 'ready' field"
                );
            }

            String reason = readiness.hasNonNull("reason")
                    ? readiness.get("reason").asText()
                    : "No readiness reason provided";

            if (readyNode.asBoolean()) {
                return RecoveryEvaluation.healthy(
                        "Pod " + podName + " is ready"
                );
            }

            return RecoveryEvaluation.unhealthy(
                    "Pod " + podName + " is not ready: " + reason
            );

        } catch (Exception exception) {
            return RecoveryEvaluation.unknown(
                    "Failed to evaluate pod readiness: "
                            + exception.getMessage()
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}