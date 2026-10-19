
package com.agents_service.service;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.agents_service.service.IncidentOrchestrator;

import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

@Component
@ConditionalOnProperty(
        prefix = "agent.investigation.worker",
        name = "enabled",
        havingValue = "true")
public class InvestigationJobWorker {

    private static final Logger log =
            LoggerFactory.getLogger(InvestigationJobWorker.class);

    private final JdbcTemplate jdbcTemplate;
    private final IncidentOrchestrator orchestrator;
    private final IncidentService incidentService;
    private final int maxAttempts;
    private final int staleLockSeconds;

    public InvestigationJobWorker(
            JdbcTemplate jdbcTemplate,
            IncidentOrchestrator orchestrator,
            IncidentService incidentService,
            @Value("${agent.investigation.worker.max-attempts:3}")
            int maxAttempts,
            @Value("${agent.investigation.worker.stale-lock-seconds:900}")
            int staleLockSeconds) {

        this.jdbcTemplate = jdbcTemplate;
        this.orchestrator = orchestrator;
        this.incidentService = incidentService;
        this.maxAttempts = maxAttempts;
        this.staleLockSeconds = staleLockSeconds;
    }

    @Scheduled(
        fixedDelayString =
            "${agent.investigation.worker.poll-interval-ms:2000}")
    public void pollQueue() {
        try {
            failExhaustedStaleJobs();

            // Process one job per poll. The next poll happens after
            // this invocation finishes and the configured delay passes.
            List<JobClaim> jobs = claimNextJob();

            if (!jobs.isEmpty()) {
                process(jobs.get(0));
            }
        } catch (Exception exception) {
            log.error("Investigation queue polling failed", exception);
        }
    }

    private List<JobClaim> claimNextJob() {
        String sql = """
            WITH candidate AS (
                SELECT incident_id
                FROM incident_investigation_jobs
                WHERE (
                    (
                        status IN ('PENDING', 'RETRY')
                        AND available_at <= NOW()
                    )
                    OR
                    (
                        status = 'RUNNING'
                        AND locked_at IS NOT NULL
                        AND locked_at <
                            NOW() - (? * INTERVAL '1 second')
                    )
                )
                AND attempts < ?
                ORDER BY available_at, created_at
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            UPDATE incident_investigation_jobs AS job
            SET status = 'RUNNING',
                attempts = job.attempts + 1,
                locked_at = NOW(),
                updated_at = NOW(),
                last_error = NULL
            FROM candidate
            WHERE job.incident_id = candidate.incident_id
            RETURNING job.incident_id, job.attempts, job.locked_at
            """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new JobClaim(
                        rs.getObject("incident_id", UUID.class),
                        rs.getInt("attempts"),
                        rs.getTimestamp("locked_at")),
                staleLockSeconds,
                maxAttempts);
    }

    private void process(JobClaim job) {
        log.info(
                "Starting investigation for incident {}, attempt {}",
                job.incidentId(),
                job.attempts());

        try {
            orchestrator.investigate(job.incidentId());
        } catch (Exception exception) {
            handleFailure(job, exception);
            return;
        }

        int updated = jdbcTemplate.update("""
            UPDATE incident_investigation_jobs
            SET status = 'COMPLETED',
                locked_at = NULL,
                last_error = NULL,
                updated_at = NOW()
            WHERE incident_id = ?
              AND status = 'RUNNING'
              AND locked_at = ?
            """,
            job.incidentId(),
            job.lockedAt());

        if (updated == 0) {
            log.warn(
                    "Job lease was lost before completion for incident {}",
                    job.incidentId());
            return;
        }

        recordJobEvent(
                job.incidentId(),
                "INVESTIGATION_JOB_COMPLETED",
                "attempts",
                job.attempts());

        log.info(
                "Investigation completed for incident {}",
                job.incidentId());
    }

    private void handleFailure(
            JobClaim job,
            Exception exception) {

        String error = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();

        // Avoid storing arbitrarily large exception messages.
        if (error.length() > 4000) {
            error = error.substring(0, 4000);
        }

        boolean exhausted = job.attempts() >= maxAttempts;

        if (exhausted) {
            int updated = jdbcTemplate.update("""
                UPDATE incident_investigation_jobs
                SET status = 'FAILED',
                    locked_at = NULL,
                    last_error = ?,
                    updated_at = NOW()
                WHERE incident_id = ?
                  AND status = 'RUNNING'
                  AND locked_at = ?
                """,
                error,
                job.incidentId(),
                job.lockedAt());

            if (updated == 0) {
                log.warn(
                        "Could not mark job failed because its lease changed: {}",
                        job.incidentId());
                return;
            }

            recordJobEvent(
                    job.incidentId(),
                    "INVESTIGATION_JOB_FAILED",
                    "error",
                    error);

            try {
                incidentService.changeState(
                        job.incidentId(), "FAILED");
            } catch (Exception stateException) {
                log.error(
                        "Could not transition incident {} to FAILED",
                        job.incidentId(),
                        stateException);
            }

            log.error(
                    "Investigation exhausted retries for incident {}",
                    job.incidentId(),
                    exception);
            return;
        }

        // Exponential backoff: 5 seconds, 10 seconds, 20 seconds...
        int delaySeconds =
                Math.min(300, 5 * (1 << (job.attempts() - 1)));

        int updated = jdbcTemplate.update("""
            UPDATE incident_investigation_jobs
            SET status = 'RETRY',
                available_at = NOW() + (? * INTERVAL '1 second'),
                locked_at = NULL,
                last_error = ?,
                updated_at = NOW()
            WHERE incident_id = ?
              AND status = 'RUNNING'
              AND locked_at = ?
            """,
            delaySeconds,
            error,
            job.incidentId(),
            job.lockedAt());

        if (updated == 0) {
            log.warn(
                    "Could not schedule retry because the job lease changed: {}",
                    job.incidentId());
            return;
        }

        recordJobEvent(
                job.incidentId(),
                "INVESTIGATION_JOB_RETRY_SCHEDULED",
                "delaySeconds",
                delaySeconds);

        log.warn(
                "Investigation failed for incident {}. Retry in {} seconds.",
                job.incidentId(),
                delaySeconds,
                exception);
    }

    private void failExhaustedStaleJobs() {
        List<UUID> failedIds = jdbcTemplate.query("""
            UPDATE incident_investigation_jobs
            SET status = 'FAILED',
                locked_at = NULL,
                last_error = COALESCE(
                    last_error,
                    'Worker lease expired after maximum attempts')
                ,
                updated_at = NOW()
            WHERE status = 'RUNNING'
              AND attempts >= ?
              AND locked_at IS NOT NULL
              AND locked_at <
                  NOW() - (? * INTERVAL '1 second')
            RETURNING incident_id
            """,
            (rs, rowNum) ->
                    rs.getObject("incident_id", UUID.class),
            maxAttempts,
            staleLockSeconds);

        for (UUID incidentId : failedIds) {
            recordJobEvent(
                    incidentId,
                    "INVESTIGATION_JOB_FAILED",
                    "reason",
                    "Stale job exhausted its attempts");

            try {
                incidentService.changeState(incidentId, "FAILED");
            } catch (Exception exception) {
                log.error(
                        "Could not mark stale incident {} as FAILED",
                        incidentId,
                        exception);
            }
        }
    }

    private void recordJobEvent(
            UUID incidentId,
            String eventType,
            String field,
            String value) {

        ObjectNode details =
                JsonNodeFactory.instance.objectNode();
        details.put(field, value);

        incidentService.recordEvent(
                incidentId, eventType, details);
    }

    private void recordJobEvent(
            UUID incidentId,
            String eventType,
            String field,
            int value) {

        ObjectNode details =
                JsonNodeFactory.instance.objectNode();
        details.put(field, value);

        incidentService.recordEvent(
                incidentId, eventType, details);
    }

    private record JobClaim(
            UUID incidentId,
            int attempts,
            Timestamp lockedAt) {
    }
}
