package com.agents_service.recovery;

import com.agents_service.client.ToolsServiceClient;
import com.agents_service.tool.dto.ToolResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PodReadyRecoveryEvaluatorTest {

    @Mock
    private ToolsServiceClient toolsServiceClient;

    private PodReadyRecoveryEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new PodReadyRecoveryEvaluator(
                toolsServiceClient,
                new ObjectMapper()
        );
    }

    @Test
    void returnsHealthyWhenPodIsReady() {
        mockSuccessfulResponse("""
                {"ready": true}
                """);

        RecoveryEvaluation result = evaluator.evaluate(policy());

        assertEquals(RecoveryStatus.HEALTHY, result.status());
    }

    @Test
    void returnsUnhealthyWhenPodIsNotReady() {
        mockSuccessfulResponse("""
                {"ready": false}
                """);

        RecoveryEvaluation result = evaluator.evaluate(policy());

        assertEquals(RecoveryStatus.UNHEALTHY, result.status());
    }

    @Test
    void returnsUnknownWhenToolCallFails() {
        ToolResponse response = new ToolResponse();
        response.setSuccess(false);
        response.setError("Tools Service unavailable");

        when(toolsServiceClient.executeTool(any()))
                .thenReturn(response);

        RecoveryEvaluation result = evaluator.evaluate(policy());

        assertEquals(RecoveryStatus.UNKNOWN, result.status());
    }

    @Test
    void returnsUnknownWhenResponseIsMalformed() {
        mockSuccessfulResponse("not-json");

        RecoveryEvaluation result = evaluator.evaluate(policy());

        assertEquals(RecoveryStatus.UNKNOWN, result.status());
    }

    @Test
    void returnsUnknownWhenReadyFieldIsMissing() {
        mockSuccessfulResponse("""
                {"phase": "Running"}
                """);

        RecoveryEvaluation result = evaluator.evaluate(policy());

        assertEquals(RecoveryStatus.UNKNOWN, result.status());
    }

    private RecoveryPolicy policy() {
        return new RecoveryPolicy(
                "podReady",
                Map.of(
                        "targetId", "test-cluster",
                        "namespace", "default",
                        "podName", "test-pod"
                ),
                3,
                30
        );
    }

    private void mockSuccessfulResponse(String responseBody) {
        ToolResponse response = new ToolResponse();
        response.setSuccess(true);
        response.setResponse(responseBody);

        when(toolsServiceClient.executeTool(any()))
                .thenReturn(response);
    }
}