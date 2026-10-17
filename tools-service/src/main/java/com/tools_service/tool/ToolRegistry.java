package com.tools_service.tool;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ToolRegistry {

    private final Map<String, ToolHandler> handlers = new HashMap<>();

    public ToolRegistry(KubernetesToolHandler kubernetesToolHandler, PrometheusToolHandler prometheusToolHandler) {

        handlers.put("getPods", kubernetesToolHandler);
        handlers.put("getPodLogs", kubernetesToolHandler);
        handlers.put("queryPrometheus", prometheusToolHandler);
    }

    public ToolHandler getHandler(String toolName) {

        ToolHandler handler = handlers.get(toolName);

        if (handler == null) {
            throw new IllegalArgumentException(
                    "Unknown tool: " + toolName
            );
        }

        return handler;
    }
}