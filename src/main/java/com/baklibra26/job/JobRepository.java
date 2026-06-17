package com.baklibra26.job;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {

    Optional<Job> findByIdempotencyKey(String idempotencyKey);

    List<Job> findJobsByStatusIs(JobStatus status);

    List<Job> findJobsByStatusOrderByCreatedAtAsc(JobStatus status, Pageable pageable);

    List<Job> findJobsByStatusAndWorkerJobIdIsNotNullOrderByCreatedAtAsc(JobStatus status, Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query(
            value = """
                    INSERT INTO jobs (
                        id,
                        idempotency_key,
                        image_url,
                        status,
                        lease_type,
                        created_at,
                        updated_at
                    )
                    VALUES (
                        :id,
                        :idempotencyKey,
                        :imageUrl,
                        'PENDING',
                        'NONE',
                        :now,
                        :now
                    )
                    ON DUPLICATE KEY UPDATE id = id
                    """,
            nativeQuery = true
    )
    void saveIfAbsent(
            @Param("id") UUID id,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("imageUrl") String imageUrl,
            @Param("now") Instant now
    );

    @Query(
        value = """
                    SELECT *
                     FROM jobs
                     WHERE status = 'PENDING'
                       AND lease_type = 'NONE'
                     ORDER BY created_at ASC
                     LIMIT :batchSize
                     FOR UPDATE SKIP LOCKED
                """,
        nativeQuery = true
    )
    List<Job> findSubmitCandidatesForUpdateSkipLocked(
            @Param("batchSize") int batchSize
    );

    @Query(
            value = """
                    SELECT *
                      FROM jobs
                     WHERE status = 'PROCESSING'
                       AND worker_job_id IS NOT NULL
                       AND lease_type = 'NONE'
                     ORDER BY created_at ASC
                     LIMIT :batchSize
                     FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true
    )
    List<Job> findPollCandidatesForUpdateSkipLocked(
            @Param("batchSize") int batchSize
    );

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Job j
           SET j.status = com.baklibra26.job.JobStatus.PROCESSING,
                   j.workerJobId = :workerJobId,
                   j.submittedAt = :now,
                   j.leaseType = com.baklibra26.job.JobLeaseType.NONE,
                   j.leasedUntil = null,
                   j.updatedAt = :now
         WHERE j.id = :jobId
           AND j.status = com.baklibra26.job.JobStatus.PENDING
           AND j.leaseType = com.baklibra26.job.JobLeaseType.SUBMIT
    """)
    int markSubmitSucceeded(
        UUID jobId,
        String workerJobId,
        Instant now
    );

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Job j
               SET j.status = com.baklibra26.job.JobStatus.FAILED,
                   j.result = :result,
                   j.finishedAt = :now,
                   j.leaseType = com.baklibra26.job.JobLeaseType.NONE,
                   j.leasedUntil = null,
                   j.updatedAt = :now
             WHERE j.id = :jobId
               AND j.status = com.baklibra26.job.JobStatus.PENDING
               AND j.leaseType = com.baklibra26.job.JobLeaseType.SUBMIT
            """)
    int markSubmitFailed(
            UUID jobId,
            String result,
            Instant now
    );

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Job j
               SET j.status = com.baklibra26.job.JobStatus.COMPLETED,
                   j.result = :result,
                   j.finishedAt = :now,
                   j.leaseType = com.baklibra26.job.JobLeaseType.NONE,
                   j.leasedUntil = null,
                   j.updatedAt = :now
             WHERE j.id = :jobId
               AND j.status = com.baklibra26.job.JobStatus.PROCESSING
               AND j.leaseType = com.baklibra26.job.JobLeaseType.POLL
            """)
    int markPollCompleted(
            UUID jobId,
            String result,
            Instant now
    );

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Job j
               SET j.status = com.baklibra26.job.JobStatus.FAILED,
                   j.result = :result,
                   j.finishedAt = :now,
                   j.leaseType = com.baklibra26.job.JobLeaseType.NONE,
                   j.leasedUntil = null,
                   j.updatedAt = :now
             WHERE j.id = :jobId
               AND j.status = com.baklibra26.job.JobStatus.PROCESSING
               AND j.leaseType = com.baklibra26.job.JobLeaseType.POLL
            """)
    int markPollFailed(
            UUID jobId,
            String result,
            Instant now
    );

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Job j
               SET j.leaseType = com.baklibra26.job.JobLeaseType.NONE,
                   j.leasedUntil = null,
                   j.updatedAt = :now
             WHERE j.leaseType <> com.baklibra26.job.JobLeaseType.NONE
               AND j.leasedUntil < :now
            """)
    int releaseExpiredLeases(Instant now);

}
