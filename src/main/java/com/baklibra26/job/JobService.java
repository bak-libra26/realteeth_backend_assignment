package com.baklibra26.job;

import com.baklibra26.job.exceptions.IdempotencyConflictException;
import com.baklibra26.job.exceptions.JobNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;

    @Transactional
    public Job createJob(String idempotencyKey, String imageUrl) {
        jobRepository.saveIfAbsent(
                UUID.randomUUID(),
                idempotencyKey,
                imageUrl,
                Instant.now()
        );

        return getJobByIdempotencyKeyAndImageUrl(idempotencyKey, imageUrl)
                .orElseThrow(() -> new IdempotencyConflictException(idempotencyKey));
    }

    private Optional<Job> getJobByIdempotencyKeyAndImageUrl(String idempotencyKey, String imageUrl) {
        return jobRepository.findByIdempotencyKey(idempotencyKey)
                .filter(job -> imageUrl.equals(job.getImageUrl()));
    }


    public Job getJobById(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
    }

    public Page<Job> getJobs(int page, int size) {
        return jobRepository.findAll(PageRequest.of(page, size));
    }

    @Transactional
    public List<Job> claimJobsForPoll(int batchSize, Duration leaseTimeout) {
        Instant now = Instant.now();
        Instant leasedUntil = now.plus(leaseTimeout);
        List<Job> jobs = jobRepository.findPollCandidatesForUpdateSkipLocked(batchSize);
        if (jobs.isEmpty()) {
            return List.of();
        }

        for (Job job : jobs) {
            job.leaseForPoll(leasedUntil);
        }

        return List.copyOf(jobs);
    }

    @Transactional
    public List<Job> claimJobsForSubmit(int batchSize, Duration leaseTimeout) {
        Instant now = Instant.now();
        Instant leasedUntil = now.plus(leaseTimeout);
        List<Job> jobs = jobRepository.findSubmitCandidatesForUpdateSkipLocked(batchSize);
        if (jobs.isEmpty()) {
            return List.of();
        }

        for (Job job : jobs) {
            job.leaseForSubmit(leasedUntil);
        }

        return List.copyOf(jobs);
    }

    @Transactional
    public boolean markSubmitSucceeded(UUID jobId, String workerJobId) {
        Instant now = Instant.now();
        int updated = jobRepository.markSubmitSucceeded(
                jobId,
                workerJobId,
                now
        );

        return updated == 1;
    }

    @Transactional
    public boolean markSubmitFailed(UUID jobId, String result) {
        int updated = jobRepository.markSubmitFailed(
                jobId,
                result,
                Instant.now()
        );

        return updated == 1;
    }

    @Transactional
    public int recoverExpiredLeases() {
        return jobRepository.releaseExpiredLeases(Instant.now());
    }

    @Transactional
    public boolean markPollCompleted(UUID jobId, String result) {
        int updated = jobRepository.markPollCompleted(
                jobId,
                result,
                Instant.now()
        );

        return updated == 1;
    }

    @Transactional
    public boolean markPollFailed(UUID jobId, String result) {
        int updated = jobRepository.markPollFailed(
                jobId,
                result,
                Instant.now()
        );

        return updated == 1;
    }


}
