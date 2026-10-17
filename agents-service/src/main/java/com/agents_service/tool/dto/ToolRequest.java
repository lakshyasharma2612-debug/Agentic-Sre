package com.agents_service.tool.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter 
@NoArgsConstructor
public class ToolRequest {

    private String targetId;
    private String tool;
    private Map<String, Object> arguments;
}