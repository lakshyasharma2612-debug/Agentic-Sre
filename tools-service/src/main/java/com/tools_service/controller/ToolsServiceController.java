package com.tools_service.controller;

 
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tools_service.tool.ToolDispatcher;
import com.tools_service.tool.dto.ToolRequest;
import com.tools_service.tool.dto.ToolResponse;

import lombok.RequiredArgsConstructor;

@RestController 
@RequestMapping ("/tools")
@RequiredArgsConstructor 
public class ToolsServiceController {
    private final ToolDispatcher toolDispatcher;

    @GetMapping("/health")
    public String ToolsCall(){
        return "The system is working";
    }
    @PostMapping ("/execute")
     public ResponseEntity<ToolResponse> getPods(@RequestBody ToolRequest request){
        ToolResponse response= toolDispatcher.dispatch(request);
        return ResponseEntity.ok(response);
     }
}
