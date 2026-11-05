package com.agents_service.tool.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter 
@NoArgsConstructor
public class ToolResponse {

    private boolean success;
    private String response;
    private String error;
}