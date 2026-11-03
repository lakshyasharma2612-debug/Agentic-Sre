package com.agents_service.recovery;

import java.util.Map;

public record RecoveryPolicy(
        String type,
        Map<String, String> parameters,
        int consecutiveHealthyChecks,
        int intervalSeconds
) {
    public RecoveryPolicy {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Recovery policy type is required");
        }

        if (consecutiveHealthyChecks < 1) {
            throw new IllegalArgumentException(
                    "consecutiveHealthyChecks must be at least 1"
            );
        }

        if (intervalSeconds < 1) {
            throw new IllegalArgumentException(
                    "intervalSeconds must be at least 1"
            );
        }

        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}