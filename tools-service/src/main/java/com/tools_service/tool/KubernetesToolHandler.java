package com.tools_service.tool;

import tools.jackson.databind.ObjectMapper;
import com.tools_service.k8s.KubernetesService;
import com.tools_service.tool.dto.ToolRequest;
import com.tools_service.tool.dto.ToolResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor 
public class KubernetesToolHandler implements ToolHandler {

    private final KubernetesService kubernetesService;
    private final ObjectMapper objectMapper;

    @Override
    public ToolResponse execute(ToolRequest request) {

        return switch (request.getTool()) {

            case "getPods" -> getPods(request);

            case "getPodLogs" -> getPodLogs(request);

            case "getPodReadiness" -> getPodReadiness(request);

            default -> ToolResponse.failure(
                    "Unsupported Kubernetes tool: " + request.getTool()
            );
        };
    }

    private ToolResponse getPods(ToolRequest request) {

        var pods = kubernetesService.getPods(
                request.getTargetId()
        );

        return ToolResponse.success(
                pods.toString()
        );
    }

    private ToolResponse getPodReadiness(ToolRequest request) {
        Map<String, Object> args = request.getArguments();

        if (args == null) {
            return ToolResponse.failure(
                    "Arguments are required for getPodReadiness");
        }

        Object value = args.get("podName");
        String podName = value instanceof String
                ? (String) value
                : null;

        if (podName == null || podName.isBlank()) {
            return ToolResponse.failure(
                    "podName is required for getPodReadiness");
        }

        var readiness = kubernetesService.getPodReadiness(
                request.getTargetId(),
                podName);

        try {
            return ToolResponse.success(
                    objectMapper.writeValueAsString(readiness));
        } catch (Exception e) {
            return ToolResponse.failure(
                    "Could not serialize pod readiness: " + e.getMessage());
        }
    }

    private ToolResponse getPodLogs(ToolRequest request) {

        Map<String, Object> arguments = request.getArguments();

        if (arguments == null) {
            return ToolResponse.failure(
                    "Arguments are required for getPodLogs"
            );
        }

        String podName = (String) arguments.get("podName");

        Integer tailLines = arguments.get("tailLines") != null
                ? ((Number) arguments.get("tailLines")).intValue()
                : null;

        if (podName == null || podName.isBlank()) {
            return ToolResponse.failure(
                    "podName is required"
            );
        }

        String logs = kubernetesService.getPodLogs(
                request.getTargetId(),
                podName,
                tailLines
        );

        return ToolResponse.success(logs);
    }
}