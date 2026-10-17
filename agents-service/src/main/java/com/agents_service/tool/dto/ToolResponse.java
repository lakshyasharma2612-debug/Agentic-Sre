package com.agents_service.tool.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ToolResponse {

    private boolean success;
    private String response;
    private String error;
}