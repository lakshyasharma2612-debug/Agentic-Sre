CREATE TABLE incident_alerts (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL
        REFERENCES incidents(id) ON DELETE CASCADE,

    target_id VARCHAR(100) NOT NULL,
    fingerprint VARCHAR(255) NOT NULL,
    alert_name VARCHAR(255) NOT NULL,
    alert_status VARCHAR(20) NOT NULL
        CHECK (alert_status IN ('firing', 'resolved')),

    alert_payload JSONB NOT NULL DEFAULT '{}'::jsonb,

    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ
);

-- A fingerprint can be associated with multiple historical incidents,
-- but should have only one currently firing association per target.
CREATE UNIQUE INDEX uq_incident_alerts_active_fingerprint
    ON incident_alerts (target_id, fingerprint)
    WHERE alert_status = 'firing';

CREATE INDEX idx_incident_alerts_incident
    ON incident_alerts (incident_id, last_seen_at DESC);

CREATE INDEX idx_incident_alerts_fingerprint
    ON incident_alerts (target_id, fingerprint, last_seen_at DESC);