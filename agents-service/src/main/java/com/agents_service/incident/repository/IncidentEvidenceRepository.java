package com.agents_service.incident.repository;
 
import org.springframework.data.jpa.repository.JpaRepository;

import com.agents_service.incident.entity.IncidentEvidenceEntity;

import java.util.List;
import java.util.UUID;

public interface IncidentEvidenceRepository
        extends JpaRepository<IncidentEvidenceEntity, UUID> {

    List<IncidentEvidenceEntity>
    findAllByIncidentIdOrderByCreatedAtAsc(UUID incidentId);
}