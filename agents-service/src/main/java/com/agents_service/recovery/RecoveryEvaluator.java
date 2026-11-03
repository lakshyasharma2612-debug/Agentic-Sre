package com.agents_service.recovery;

public interface RecoveryEvaluator {

    /**
     * The policy type this evaluator supports.
     * Example: POD_READY or DEPLOYMENT_AVAILABLE.
     */
    String supportedType();

    /**
     * Evaluate whether the incident's recovery condition is satisfied.
     */
    RecoveryEvaluation evaluate(RecoveryPolicy policy);
}