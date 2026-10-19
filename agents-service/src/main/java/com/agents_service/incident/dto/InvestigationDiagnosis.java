
package com.agents_service.incident.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record InvestigationDiagnosis(

        @NotNull
        Status status,

        @NotBlank
        String summary,

        @NotBlank
        String probableCause,

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        @NotNull 
        Double confidence,

        @NotNull
        @NotEmpty
        List<@NotNull @Valid Observation> observations,

        @NotBlank
        String recommendedAction,

        @NotNull
        List<@NotBlank String> missingEvidence

) {
    public enum Status {
        DIAGNOSED,
        INSUFFICIENT_EVIDENCE
    }

    public record Observation(
            @NotBlank String sourceTool,
            @NotBlank String finding,
            @NotBlank String interpretation
    ) {}
}
