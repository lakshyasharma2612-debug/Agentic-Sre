package com.agents_service.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
 
import org.springframework.web.client.RestClient;

import com.agents_service.tool.dto.ToolRequest;
import com.agents_service.tool.dto.ToolResponse;

 
@Service 
public class ToolsServiceClient {
    private final RestClient restClient;
    @Value("${tools.service.url}") 
    String toolsServiceUrl; 

    public ToolsServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${tools.service.url}") String toolsServiceUrl) {

        this.restClient = restClientBuilder
                .baseUrl(toolsServiceUrl)
                .build();
    }
    public String healthCheck() {
 
        return restClient.get().uri("/tools/health").retrieve().body(String.class);
    }

    public ToolResponse executeTool ( ToolRequest request) {
    System.out.println("Target: " + request.getTargetId());
    System.out.println("Tool: " + request.getTool());
    System.out.println("Arguments: " + request.getArguments());
    return restClient
                .post()
                .uri("/tools/execute")
                .body(request)
                .retrieve()
                .body(ToolResponse.class);
    }
}
