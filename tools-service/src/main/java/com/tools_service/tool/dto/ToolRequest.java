package com.tools_service.tool.dto;

import java.util.Map;

 
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ToolRequest {

    private String targetId;
    private String tool;
    private Map<String, Object>arguments;
}