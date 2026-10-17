package com.tools_service.tool;

import com.tools_service.prometheus.PrometheusService;
import com.tools_service.tool.dto.ToolRequest;
import com.tools_service.tool.dto.ToolResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor 
public class PrometheusToolHandler implements ToolHandler {

    private final PrometheusService prometheusService;
   
    @Override
    public ToolResponse execute(ToolRequest request) {

        Map<String, Object> arguments =
                request.getArguments();

        if (arguments == null) {
            return ToolResponse.failure(
                    "Arguments are required for queryPrometheus"
            );
        }

        String query = (String) arguments.get("query");

        if (query == null || query.isBlank()) {
            return ToolResponse.failure(
                    "query is required"
            );
        }

        String result = prometheusService.query(
                request.getTargetId(),
                query
        );

        return ToolResponse.success(result);
    }
}