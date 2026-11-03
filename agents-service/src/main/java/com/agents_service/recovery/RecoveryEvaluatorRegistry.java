package com.agents_service.recovery;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class RecoveryEvaluatorRegistry {

    private final Map<String, RecoveryEvaluator> evaluators;

    public RecoveryEvaluatorRegistry(List<RecoveryEvaluator> evaluatorList) {
        this.evaluators = evaluatorList.stream()
                .collect(Collectors.toMap(
                        RecoveryEvaluator::supportedType,
                        Function.identity()
                ));
    }

    public RecoveryEvaluator getEvaluator(String type) {
        RecoveryEvaluator evaluator = evaluators.get(type);

        if (evaluator == null) {
            throw new IllegalArgumentException(
                    "No recovery evaluator registered for type: " + type
            );
        }

        return evaluator;
    }
}