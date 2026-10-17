package com.tools_service.tool;

import com.tools_service.tool.dto.ToolRequest;
import com.tools_service.tool.dto.ToolResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor 
public class ToolDispatcher {

    private final ToolRegistry toolRegistry;

    public ToolResponse dispatch(ToolRequest request) {

        try {

            if (request.getTool() == null || request.getTool().isBlank()) {
                return ToolResponse.failure(
                        "Tool name is required"
                );
            }

            ToolHandler handler =toolRegistry.getHandler(request.getTool());

            return handler.execute(request);

        } catch (Exception e) {
            return ToolResponse.failure(
                    e.getMessage()
            );
        }
    }
}