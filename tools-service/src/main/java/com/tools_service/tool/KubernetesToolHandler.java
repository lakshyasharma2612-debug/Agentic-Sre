package com.tools_service.tool;

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

    @Override
    public ToolResponse execute(ToolRequest request) {

        return switch (request.getTool()) {

            case "getPods" -> getPods(request);

            case "getPodLogs" -> getPodLogs(request);

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