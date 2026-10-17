
package com.agents_service.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LlmConnectionTest {

    private final ChatClient chatClient;

    public LlmConnectionTest(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @GetMapping("/test-llm")
    public String testLlm() {
        return chatClient
                .prompt()
                .user("Reply with exactly: LLM connection successful")
                .call()
                .content();
    }
}
