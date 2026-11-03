ALTER TABLE incident_recovery_verification_jobs
    ADD COLUMN consecutive_unhealthy_checks INTEGER NOT NULL DEFAULT 0
        CHECK (consecutive_unhealthy_checks >= 0),
    ADD COLUMN consecutive_unknown_checks INTEGER NOT NULL DEFAULT 0
        CHECK (consecutive_unknown_checks >= 0);