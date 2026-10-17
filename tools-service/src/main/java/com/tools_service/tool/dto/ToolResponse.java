package com.tools_service.tool.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ToolResponse {

    private boolean success;
    private String response;
    private String error;

    public static ToolResponse success(String response) {
        return new ToolResponse(true, response, null);
    }

    public static ToolResponse failure(String error) {
        return new ToolResponse(false, null, error);
    }
}