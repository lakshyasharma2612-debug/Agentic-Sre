CREATE TABLE incident_recovery_verification_jobs (
    incident_id UUID PRIMARY KEY
        REFERENCES incidents(id) ON DELETE CASCADE,

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN (
            'PENDING',
            'RUNNING',
            'RETRY',
            'COMPLETED',
            'FAILED'
        )),

    attempts INTEGER NOT NULL DEFAULT 0
        CHECK (attempts >= 0),

    available_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    locked_at TIMESTAMPTZ,
    last_error TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recovery_verification_jobs_pending
    ON incident_recovery_verification_jobs (available_at, created_at)
    WHERE status IN ('PENDING', 'RETRY');