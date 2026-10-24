package com.agents_service.incident.repository;

import com.agents_service.incident.entity.IncidentAlertEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncidentAlertRepository
        extends JpaRepository<IncidentAlertEntity, UUID> {

    Optional<IncidentAlertEntity>
    findFirstByTargetIdAndFingerprintAndAlertStatusOrderByLastSeenAtDesc(
            String targetId,
            String fingerprint,
            String alertStatus
    );

    List<IncidentAlertEntity>
    findAllByIncidentIdOrderByLastSeenAtDesc(UUID incidentId);

    boolean existsByIncidentIdAndAlertStatus(
            UUID incidentId,
            String alertStatus
    );
}