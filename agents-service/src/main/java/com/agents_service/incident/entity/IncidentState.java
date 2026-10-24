package com.agents_service.incident.entity;

 
import java.util.EnumSet;
import java.util.Set;

public enum IncidentState {

    INVESTIGATING,
    DIAGNOSED,
    ACTING,
    VERIFYING,
    RESOLVED,
    ESCALATED,
    FAILED,
    TIMED_OUT,
    SUPERSEDED;

    private static final Set<IncidentState> TERMINAL_STATES =
            EnumSet.of(
                    RESOLVED,
                    ESCALATED,
                    FAILED,
                    TIMED_OUT,
                    SUPERSEDED
            );

    public boolean isTerminal() {
        return TERMINAL_STATES.contains(this);
    }

    public boolean canTransitionTo(IncidentState next) {
        if (next == null || isTerminal() || this == next) {
            return false;
        }

        if (next == ESCALATED
                || next == FAILED
                || next == TIMED_OUT
                || next == SUPERSEDED) {
            return true;
        }

        return switch (this) {
            case INVESTIGATING -> next == DIAGNOSED;
            case DIAGNOSED -> next == ACTING||next==VERIFYING;
            case ACTING -> next == VERIFYING;
            case VERIFYING ->
                    next == RESOLVED || next == INVESTIGATING;
            default -> false;
        };
    }
}
