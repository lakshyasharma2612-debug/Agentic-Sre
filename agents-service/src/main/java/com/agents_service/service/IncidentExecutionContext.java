
package com.agents_service.service;

import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class IncidentExecutionContext {

    private final ThreadLocal<UUID> currentIncidentId =
            new ThreadLocal<>();

    public UUID currentIncidentId() {
        return currentIncidentId.get();
    }

    public Scope open(UUID incidentId) {
        UUID previous = currentIncidentId.get();
        currentIncidentId.set(incidentId);

        return () -> {
            if (previous == null) {
                currentIncidentId.remove();
            } else {
                currentIncidentId.set(previous);
            }
        };
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
