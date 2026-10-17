package com.agents_service;

 
import org.junit.jupiter.api.Test;

import com.agents_service.incident.entity.IncidentState;

import static org.junit.jupiter.api.Assertions.*;

class IncidentStateTest {

    @Test
    void allowsExpectedLifecycle() {
        assertTrue(IncidentState.INVESTIGATING
                .canTransitionTo(IncidentState.DIAGNOSED));

        assertTrue(IncidentState.DIAGNOSED
                .canTransitionTo(IncidentState.ACTING));

        assertTrue(IncidentState.ACTING
                .canTransitionTo(IncidentState.VERIFYING));

        assertTrue(IncidentState.VERIFYING
                .canTransitionTo(IncidentState.RESOLVED));
    }

    @Test
    void allowsReinvestigationAfterFailedVerification() {
        assertTrue(IncidentState.VERIFYING
                .canTransitionTo(IncidentState.INVESTIGATING));
    }

    @Test
    void rejectsInvalidTransitions() {
        assertFalse(IncidentState.INVESTIGATING
                .canTransitionTo(IncidentState.RESOLVED));

        assertFalse(IncidentState.DIAGNOSED
                .canTransitionTo(IncidentState.RESOLVED));

        assertFalse(IncidentState.ACTING
                .canTransitionTo(IncidentState.DIAGNOSED));
    }

    @Test
    void terminalStatesCannotTransition() {
        for (IncidentState state : IncidentState.values()) {
            if (state.isTerminal()) {
                assertFalse(state.canTransitionTo(
                        IncidentState.INVESTIGATING));
            }
        }
    }
}
