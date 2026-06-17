package com.baklibra26.job.workers;

import com.baklibra26.common.configuration.JobWorkerProperties;
import com.baklibra26.mockworker.exceptions.MockWorkerHttpException;
import com.baklibra26.mockworker.exceptions.MockWorkerInvalidResponseException;
import com.baklibra26.mockworker.exceptions.MockWorkerNetworkException;
import com.baklibra26.job.Job;
import com.baklibra26.job.JobService;
import com.baklibra26.mockworker.MockWorkerApiKeyProvider;
import com.baklibra26.mockworker.MockWorkerClient;
import com.baklibra26.mockworker.MockWorkerPayloads;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Component
public class JobSubmitWorker {

    private final Executor executor;
    private final JobWorkerProperties.Submit property;

    private final MockWorkerClient client;
    private final MockWorkerApiKeyProvider apiKeyProvider;
    private final JobService jobService;

    public JobSubmitWorker(
            @Qualifier("jobSubmitExecutor")
            Executor jobSubmitExecutor,
            JobWorkerProperties properties,
            JobService jobService,
            MockWorkerClient mockWorkerClient,
            MockWorkerApiKeyProvider apiKeyProvider
    ) {
        this.executor = jobSubmitExecutor;
        this.property = properties.submit();

        this.jobService = jobService;
        this.client = mockWorkerClient;
        this.apiKeyProvider = apiKeyProvider;
    }

    @Scheduled(fixedDelayString = "#{@environment.getProperty('img-proc.job-worker.submit.interval', T(java.time.Duration)).toMillis()}")
    public void submitPendingJobs() {
        if (!apiKeyProvider.hasApiKey()) {
            log.debug("job submit skipped because Mock Worker API key is not ready");
            return;
        }

        Instant startedAt = Instant.now();
        List<Job> jobs = jobService.claimJobsForSubmit(property.batchSize(), property.leaseTimeout());
        if (jobs.isEmpty()) {
            log.debug("job submit batch skipped. reason=no claimable jobs, batchSize={}", property.batchSize());
            return;
        }

        log.info("job submit batch started. claimed={}, batchSize={}, concurrency={}, leaseTimeout={}", jobs.size(), property.batchSize(), property.concurrency(), property.leaseTimeout());
        List<CompletableFuture<Void>> futures = jobs.stream()
                .map(job -> CompletableFuture.runAsync(() -> submit(job), executor)
                                    .exceptionally((e) -> {
                                        log.error(
                                            "job submit task failed. job lease may be released by lease recovery worker. jobId={}",
                                            job.getId(),
                                            e
                                        );

                                        return null;
                                    }))
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        log.info("job submit batch finished. claimed={}, elapsedMs={}", jobs.size(), Duration.between(startedAt, Instant.now()).toMillis());
    }

    private void submit(Job job) {
        try {
            MockWorkerPayloads.ProcessStartResponse response = client.submit(job, apiKeyProvider.getApiKey());
            boolean updated = jobService.markSubmitSucceeded(job.getId(), response.jobId());
            if (!updated) {
                log.warn("job submit status transition skipped. jobId={}, workerJobId={}, from=PENDING/SUBMIT lease, to=PROCESSING", job.getId(), response.jobId());
                return;
            }

            log.info(
                    "job submitted. jobId={}, workerJobId={}, workerStatus={}",
                    job.getId(),
                    response.jobId(),
                    response.status()
            );
        } catch (MockWorkerInvalidResponseException | MockWorkerNetworkException e) {
            log.warn("job submit deferred to recovery. jobId={}, reason={}, leaseTimeout={}, detail={}", job.getId(), e.getClass().getSimpleName(), property.leaseTimeout(), e.getMessage());
        } catch (MockWorkerHttpException e) {
            if (e.isRateLimited() || e.isServerError()) {
                log.warn("job submit deferred to recovery. jobId={}, reason=http error, statusCode={}, leaseTimeout={}, detail={}", job.getId(), e.getStatusCode(), property.leaseTimeout(), e.getBody().message());
                return;
            }

            markSubmitFailed(job, e);
        }
    }

    private void markSubmitFailed(Job job, MockWorkerHttpException e) {
        boolean updated = jobService.markSubmitFailed(job.getId(), e.getMessage());
        if (!updated) {
            log.warn("job submit failure transition skipped. jobId={}, statusCode={}, detail={}, to=FAILED", job.getId(), e.getStatusCode(), e.getBody().message());
            return;
        }

        log.warn("job submit failed. jobId={}, statusCode={}, detail={}", job.getId(), e.getStatusCode(), e.getBody().message());
    }

}
