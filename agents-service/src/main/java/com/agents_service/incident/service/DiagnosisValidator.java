
package com.agents_service.incident.service;

import org.springframework.stereotype.Component;

import com.agents_service.incident.dto.InvestigationDiagnosis;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

@Component
public class DiagnosisValidator {

    private final Validator validator;

    public DiagnosisValidator(Validator validator) {
        this.validator = validator;
    }

    public void validate(InvestigationDiagnosis diagnosis) {
        if (diagnosis == null) {
            throw new IllegalArgumentException(
                    "Diagnosis must not be null");
        }

        var violations = validator.validate(diagnosis);

        if (!violations.isEmpty()) {
            String errors = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("Invalid diagnosis");

            throw new IllegalArgumentException(
                    "Diagnosis validation failed: " + errors);
        }

        if (diagnosis.status()
                == InvestigationDiagnosis.Status.DIAGNOSED
                && diagnosis.observations().isEmpty()) {
            throw new IllegalArgumentException(
                    "A diagnosed incident requires observations");
        }

        if (diagnosis.status()
                == InvestigationDiagnosis.Status.INSUFFICIENT_EVIDENCE
                && diagnosis.missingEvidence().isEmpty()) {
            throw new IllegalArgumentException(
                    "An inconclusive diagnosis must explain what evidence is missing");
        }
    }
}
