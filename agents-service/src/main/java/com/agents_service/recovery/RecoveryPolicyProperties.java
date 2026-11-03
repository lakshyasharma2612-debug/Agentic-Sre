package com.agents_service.recovery;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "recovery")
public record RecoveryPolicyProperties(
        Map<String, RecoveryPolicy> policies
) {
    public RecoveryPolicyProperties {
        policies = policies == null ? Map.of() : Map.copyOf(policies);
    }

    public RecoveryPolicy getPolicy(String alertName) {
        return policies.get(alertName);
    }
}