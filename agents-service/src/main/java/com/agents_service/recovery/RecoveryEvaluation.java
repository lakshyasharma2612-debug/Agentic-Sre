package com.agents_service.recovery;

public record RecoveryEvaluation(
        RecoveryStatus status,
        String reason
) {
    public static RecoveryEvaluation healthy(String reason) {
        return new RecoveryEvaluation(RecoveryStatus.HEALTHY, reason);
    }

    public static RecoveryEvaluation unhealthy(String reason) {
        return new RecoveryEvaluation(RecoveryStatus.UNHEALTHY, reason);
    }

    public static RecoveryEvaluation unknown(String reason) {
        return new RecoveryEvaluation(RecoveryStatus.UNKNOWN, reason);
    }
}