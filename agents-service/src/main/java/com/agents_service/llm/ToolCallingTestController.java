
package com.agents_service.llm;

import com.agents_service.tool.AgentTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ToolCallingTestController {

    private final ChatClient chatClient;

    public ToolCallingTestController(
            ChatClient.Builder builder,
            AgentTools agentTools) {

        this.chatClient = builder
                .defaultTools(agentTools)
                .build();
    }

    @GetMapping("/test-tool-calling")
    public String testToolCalling() {
        return chatClient
                .prompt()
                .system("""
                    You are an SRE investigating a Kubernetes incident.
                    Use the available tools to inspect the actual infrastructure.
                    Do not invent tool results.
                    Summarize what the tool results show.
                    """)
                .user("""
                    Investigate the current Kubernetes pods.
                    You must call getPods before answering.
                    """)
                .call()
                .content();
    }
}
