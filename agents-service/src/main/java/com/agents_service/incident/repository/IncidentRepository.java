package com.agents_service.incident.repository;

 

 
import org.springframework.data.jpa.repository.JpaRepository;

import com.agents_service.incident.entity.IncidentEntity;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface IncidentRepository
        extends JpaRepository<IncidentEntity, UUID> {

    Optional<IncidentEntity>
    findFirstByTargetIdAndDedupKeyAndStateNotInOrderByCreatedAtDesc(
        String targetId,
        String dedupKey,
        Set<String> terminalStates
    );
}