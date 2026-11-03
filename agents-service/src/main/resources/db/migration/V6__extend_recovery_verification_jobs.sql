ALTER TABLE incident_recovery_verification_jobs
    ADD COLUMN recovery_policy JSONB,
    ADD COLUMN consecutive_healthy_checks INTEGER NOT NULL DEFAULT 0
        CHECK (consecutive_healthy_checks >= 0);