package com.baklibra26.job.workers;

import com.baklibra26.common.configuration.JobWorkerProperties;
import com.baklibra26.job.Job;
import com.baklibra26.job.JobService;
import com.baklibra26.mockworker.MockWorkerApiKeyProvider;
import com.baklibra26.mockworker.MockWorkerClient;
import com.baklibra26.mockworker.MockWorkerPayloads;
import com.baklibra26.mockworker.exceptions.MockWorkerHttpException;
import com.baklibra26.mockworker.exceptions.MockWorkerInvalidResponseException;
import com.baklibra26.mockworker.exceptions.MockWorkerNetworkException;
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
public class JobPollWorker {

    private final Executor executor;
    private final MockWorkerClient client;
    private final MockWorkerApiKeyProvider apiKeyProvider;

    private final JobService jobService;
    private final JobWorkerProperties.Poll property;

    public JobPollWorker(
            @Qualifier("jobPollExecutor")
            Executor jobPollExecutor,
            JobWorkerProperties properties,
            JobService jobService,
            MockWorkerClient mockWorkerClient,
            MockWorkerApiKeyProvider apiKeyProvider
    ) {
        this.executor = jobPollExecutor;
        this.property = properties.poll();
        this.jobService = jobService;
        this.client = mockWorkerClient;
        this.apiKeyProvider = apiKeyProvider;
    }

    @Scheduled(fixedDelayString = "#{@environment.getProperty('img-proc.job-worker.poll.interval', T(java.time.Duration)).toMillis()}")
    public void pollProcessingJobs() {
        if (!apiKeyProvider.hasApiKey()) {
            log.debug("job poll skipped because Mock Worker API key is not ready");
            return;
        }

        Instant startedAt = Instant.now();
        List<Job> jobs = jobService.claimJobsForPoll(property.batchSize(), property.leaseTimeout());
        if (jobs.isEmpty()) {
            log.debug("job poll batch skipped. reason=no claimable jobs, batchSize={}", property.batchSize());
            return;
        }

        log.info("job poll batch started. claimed={}, batchSize={}, concurrency={}, leaseTimeout={}", jobs.size(), property.batchSize(), property.concurrency(), property.leaseTimeout());
        List<CompletableFuture<Void>> futures = jobs.stream()
                .map(job -> CompletableFuture.runAsync(() -> poll(job), executor)
                        .exceptionally(e -> {
                            log.error(
                                    "job poll task failed. job lease may be released by lease recovery worker. jobId={}, workerJobId={}",
                                    job.getId(),
                                    job.getWorkerJobId(),
                                    e
                            );

                            return null;
                        }))
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        log.info("job poll batch finished. claimed={}, elapsedMs={}", jobs.size(), Duration.between(startedAt, Instant.now()).toMillis());
    }

    private void poll(Job job) {
        try {
            MockWorkerPayloads.ProcessStatusResponse response = client.getStatus(job.getWorkerJobId(), apiKeyProvider.getApiKey());
            updateJobStatus(job, response);
        } catch (MockWorkerInvalidResponseException e) {
            log.warn("job poll deferred to recovery. jobId={}, workerJobId={}, reason=invalid mock worker response, leaseTimeout={}, detail={}", job.getId(), job.getWorkerJobId(), property.leaseTimeout(), e.getMessage());
        } catch (MockWorkerNetworkException e) {
            log.warn("job poll deferred to recovery. jobId={}, workerJobId={}, reason=network error, leaseTimeout={}, detail={}", job.getId(), job.getWorkerJobId(), property.leaseTimeout(), e.getMessage());
        } catch (MockWorkerHttpException e) {
            if (e.isRateLimited() || e.isServerError()) {
                log.warn(
                        "job poll deferred to recovery. jobId={}, workerJobId={}, reason=retryable http error, statusCode={}, leaseTimeout={}, detail={}",
                        job.getId(),
                        job.getWorkerJobId(),
                        e.getStatusCode(),
                        property.leaseTimeout(),
                        e.getBody().message()
                );
                return;
            }

            boolean markFailed = jobService.markPollFailed(job.getId(), e.getMessage());
            if (!markFailed) {
                log.warn(
                        "job poll failure transition skipped. jobId={}, workerJobId={}, statusCode={}, detail={}, to=FAILED",
                        job.getId(),
                        job.getWorkerJobId(),
                        e.getStatusCode(),
                        e.getBody().message()
                );
                return;
            }

            log.warn("job poll failed. jobId={}, workerJobId={}, statusCode={}, detail={}", job.getId(), job.getWorkerJobId(), e.getStatusCode(), e.getBody().message());
        }
    }

    private void updateJobStatus(Job job, MockWorkerPayloads.ProcessStatusResponse response) {
        switch (response.status()) {
            case PROCESSING:
                log.debug("job poll deferred to recovery. jobId={}, workerJobId={}, workerStatus={}, leaseTimeout={}", job.getId(), job.getWorkerJobId(), response.status(), property.leaseTimeout());
                break;

            case COMPLETED:
                boolean markPollCompleted = jobService.markPollCompleted(job.getId(), response.result());
                if (!markPollCompleted) {
                    log.warn("job poll completion transition skipped. jobId={}, workerJobId={}, to=COMPLETED", job.getId(), job.getWorkerJobId());
                    break;
                }

                log.info("job completed. jobId={}, workerJobId={}", job.getId(), job.getWorkerJobId());
                break;

            case FAILED:
                boolean markFailed = jobService.markPollFailed(job.getId(), response.result());
                if (!markFailed) {
                    log.warn("job poll failure transition skipped. jobId={}, workerJobId={}, to=FAILED", job.getId(), job.getWorkerJobId());
                    break;
                }

                log.warn("job failed by mock worker. jobId={}, workerJobId={}", job.getId(), job.getWorkerJobId());
                break;

            default:
                log.warn(
                        "job poll returned unexpected worker status. jobId={}, workerJobId={}, workerStatus={}",
                        job.getId(),
                        job.getWorkerJobId(),
                        response.status()
                );
                break;
        }
    }

}
