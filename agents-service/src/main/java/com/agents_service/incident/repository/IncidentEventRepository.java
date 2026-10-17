package com.agents_service.incident.repository;
 
import org.springframework.data.jpa.repository.JpaRepository;

import com.agents_service.incident.entity.IncidentEventEntity;

import java.util.List;
import java.util.UUID;

public interface IncidentEventRepository
        extends JpaRepository<IncidentEventEntity, UUID> {

    List<IncidentEventEntity>
    findAllByIncidentIdOrderByCreatedAtAsc(UUID incidentId);
}