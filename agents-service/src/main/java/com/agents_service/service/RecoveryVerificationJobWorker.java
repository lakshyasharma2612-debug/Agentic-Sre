package com.agents_service.service;

import com.agents_service.recovery.RecoveryEvaluation;
import com.agents_service.recovery.RecoveryEvaluator;
import com.agents_service.recovery.RecoveryEvaluatorRegistry;
import com.agents_service.recovery.RecoveryPolicy;
 
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "agent.recovery.worker",
        name = "enabled",
        havingValue = "true"
)
public class RecoveryVerificationJobWorker {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RecoveryEvaluatorRegistry evaluatorRegistry;
    private final IncidentService incidentService;

    private static final int MAX_ATTEMPTS = 3;
    private static final int STALE_LOCK_SECONDS = 900;

    @Scheduled(
            fixedDelayString = "${agent.recovery.worker.poll-interval-ms:2000}"
    )
    public void pollQueue() {
        List<RecoveryJobClaim> jobs = claimJobs();

        for (RecoveryJobClaim job : jobs) {
            process(job);
        }
    }

    private List<RecoveryJobClaim> claimJobs() {
        return jdbcTemplate.query("""
            WITH candidates AS (
                SELECT incident_id
                FROM incident_recovery_verification_jobs
                WHERE attempts < ?
                  AND (
                        (
                            status IN ('PENDING', 'RETRY')
                            AND available_at <= NOW()
                        )
                        OR
                        (
                            status = 'RUNNING'
                            AND locked_at < NOW() - (? * INTERVAL '1 second')
                        )
                  )
                ORDER BY available_at, created_at
                FOR UPDATE SKIP LOCKED
                LIMIT 10
            )
            UPDATE incident_recovery_verification_jobs job
            SET
                status = 'RUNNING',
                attempts = job.attempts + 1,
                locked_at = NOW(),
                updated_at = NOW()
            FROM candidates
            WHERE job.incident_id = candidates.incident_id
            RETURNING
                job.incident_id,
                job.attempts,
                job.locked_at,
                job.recovery_policy::text
            """,
            (rs, rowNum) -> new RecoveryJobClaim(
                    rs.getObject("incident_id", UUID.class),
                    rs.getInt("attempts"),
                    rs.getTimestamp("locked_at"),
                    rs.getString("recovery_policy")
            ),
            MAX_ATTEMPTS,
            STALE_LOCK_SECONDS
        );
    }

    private void process(RecoveryJobClaim job) {
        try {
            if (job.recoveryPolicyJson() == null) {
                throw new IllegalStateException(
                        "Recovery policy snapshot is missing for incident "
                                + job.incidentId()
                );
            }

            RecoveryPolicy policy = objectMapper.readValue(
                    job.recoveryPolicyJson(),
                    RecoveryPolicy.class
            );

            RecoveryEvaluator evaluator =
                    evaluatorRegistry.getEvaluator(policy.type());

            RecoveryEvaluation evaluation = evaluator.evaluate(policy);

            IncidentService.RecoveryApplyResult result =
                    incidentService.applyRecoveryEvaluation(
                            job.incidentId(),
                            job.lockedAt(),
                            evaluation,
                            policy
                    );

            if (result == IncidentService.RecoveryApplyResult.LEASE_LOST) {
                log.info(
                        "Recovery verification lease lost; skipping incident {}",
                        job.incidentId()
                );
                return;
            }

            if (result == IncidentService.RecoveryApplyResult.INCIDENT_NOT_VERIFYING) {
                markCompletedIfLeaseOwned(job);
                log.info(
                        "Incident {} is no longer VERIFYING; recovery job completed",
                        job.incidentId()
                );
            }

        } catch (Exception e) {
            log.error(
                    "Recovery verification failed for incident {}",
                    job.incidentId(),
                    e
            );

            scheduleRetryOrFail(job, e);
        }
    }

    private void scheduleRetryOrFail(
            RecoveryJobClaim job,
            Exception exception
    ) {
        String error = exception.getMessage();

        if (error == null || error.isBlank()) {
            error = exception.getClass().getSimpleName();
        }

        if (job.attempts() >= MAX_ATTEMPTS) {
            jdbcTemplate.update("""
                UPDATE incident_recovery_verification_jobs
                SET
                    status = 'FAILED',
                    last_error = ?,
                    locked_at = NULL,
                    updated_at = NOW()
                WHERE incident_id = ?
                  AND status = 'RUNNING'
                  AND locked_at = ?
                """,
                error,
                job.incidentId(),
                job.lockedAt()
            );

            return;
        }

        int backoffSeconds = Math.min(
                30 * (1 << (job.attempts() - 1)),
                300
        );

        jdbcTemplate.update("""
            UPDATE incident_recovery_verification_jobs
            SET
                status = 'RETRY',
                available_at = NOW() + (? * INTERVAL '1 second'),
                last_error = ?,
                locked_at = NULL,
                updated_at = NOW()
            WHERE incident_id = ?
              AND status = 'RUNNING'
              AND locked_at = ?
            """,
            backoffSeconds,
            error,
            job.incidentId(),
            job.lockedAt()
        );
    }

    private void markCompletedIfLeaseOwned(RecoveryJobClaim job) {
        jdbcTemplate.update("""
            UPDATE incident_recovery_verification_jobs
            SET
                status = 'COMPLETED',
                locked_at = NULL,
                updated_at = NOW()
            WHERE incident_id = ?
              AND status = 'RUNNING'
              AND locked_at = ?
            """,
            job.incidentId(),
            job.lockedAt()
        );
    }

    private record RecoveryJobClaim(
            UUID incidentId,
            int attempts,
            Timestamp lockedAt,
            String recoveryPolicyJson
    ) {
    }
}