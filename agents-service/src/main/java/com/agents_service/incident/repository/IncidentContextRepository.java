package com.agents_service.incident.repository;
 
import org.springframework.data.jpa.repository.JpaRepository;

import com.agents_service.incident.entity.IncidentContextEntity;

import java.util.UUID;

public interface IncidentContextRepository
        extends JpaRepository<IncidentContextEntity, UUID> {
}