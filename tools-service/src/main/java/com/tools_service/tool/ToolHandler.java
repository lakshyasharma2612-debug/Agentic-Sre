package com.tools_service.tool;

import com.tools_service.tool.dto.ToolRequest;
import com.tools_service.tool.dto.ToolResponse;

public interface ToolHandler {

    ToolResponse execute(ToolRequest request);
}