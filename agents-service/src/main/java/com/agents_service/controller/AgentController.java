package com.agents_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agents_service.client.ToolsServiceClient;
import com.agents_service.tool.dto.ToolRequest;
import com.agents_service.tool.dto.ToolResponse;
import lombok.RequiredArgsConstructor;

@RestController 
@RequestMapping ("/agent")
@RequiredArgsConstructor 
public class AgentController {
    private final ToolsServiceClient toolsServiceClient;   
    @GetMapping("/health")
    public String healtservice(){
        return toolsServiceClient.healthCheck();
    }

    @PostMapping ("/execute")
    public ResponseEntity<ToolResponse> execute(@RequestBody ToolRequest request){
        return ResponseEntity.ok(toolsServiceClient.executeTool(request));
    }
}
