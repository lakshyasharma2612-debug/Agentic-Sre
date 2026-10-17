
package com.agents_service.service;

import com.agents_service.incident.entity.IncidentEntity;

public record IncidentOpenResult(
        IncidentEntity incident,
        boolean created
) {}
