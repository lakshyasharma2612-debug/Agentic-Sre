

CREATE TABLE incidents (
    id UUID PRIMARY KEY,
    target_id VARCHAR(100) NOT NULL,
    dedup_key VARCHAR(500) NOT NULL,
    alert_fingerprint VARCHAR(255),
    alert_payload JSONB NOT NULL,
    state VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Only one active incident per target and deduplication key.
-- A later recurrence can create a new incident after the old one closes.
CREATE UNIQUE INDEX uq_incidents_active_dedup
    ON incidents (target_id, dedup_key)
    WHERE state NOT IN (
        'RESOLVED', 'ESCALATED', 'FAILED',
        'TIMED_OUT', 'SUPERSEDED'
    );

CREATE TABLE incident_context (
    incident_id UUID PRIMARY KEY
        REFERENCES incidents(id) ON DELETE CASCADE,
    rolling_summary TEXT NOT NULL DEFAULT '',
    recent_messages JSONB NOT NULL DEFAULT '[]'::jsonb,
    current_hypotheses JSONB NOT NULL DEFAULT '[]'::jsonb,
    evidence_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    step_count INTEGER NOT NULL DEFAULT 0 CHECK (step_count >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE incident_events (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL
        REFERENCES incidents(id) ON DELETE CASCADE,
    event_type VARCHAR(100) NOT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_incident_events_timeline
    ON incident_events (incident_id, created_at);

CREATE TABLE incident_evidence (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL
        REFERENCES incidents(id) ON DELETE CASCADE,
    source_tool VARCHAR(100) NOT NULL,
    summary TEXT NOT NULL,
    findings JSONB NOT NULL DEFAULT '{}'::jsonb,
    truncated BOOLEAN NOT NULL DEFAULT FALSE,
    raw_reference TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_incident_evidence_timeline
    ON incident_evidence (incident_id, created_at);
