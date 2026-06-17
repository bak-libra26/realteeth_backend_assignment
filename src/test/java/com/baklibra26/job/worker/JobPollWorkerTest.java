package com.baklibra26.job.workers;

import com.baklibra26.common.configuration.JobWorkerProperties;
import com.baklibra26.job.Job;
import com.baklibra26.job.JobService;
import com.baklibra26.job.JobStatus;
import com.baklibra26.mockworker.MockWorkerApiKeyProvider;
import com.baklibra26.mockworker.MockWorkerClient;
import com.baklibra26.mockworker.MockWorkerErrorPayloads;
import com.baklibra26.mockworker.MockWorkerJobStatus;
import com.baklibra26.mockworker.MockWorkerPayloads;
import com.baklibra26.mockworker.exceptions.MockWorkerHttpException;
import com.baklibra26.mockworker.exceptions.MockWorkerInvalidResponseException;
import com.baklibra26.mockworker.exceptions.MockWorkerNetworkException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@DisplayName("JobPollWorker")
class JobPollWorkerTest {

    @Test
    @DisplayName("Mock Worker API key가 준비되지 않았으면 processing Job을 조회하지 않는다")
    void pollProcessingJobs_skipsPoll_whenApiKeyIsNotReady() {
        JobService jobService = mock(JobService.class);
        MockWorkerClient client = mock(MockWorkerClient.class);
        MockWorkerApiKeyProvider apiKeyProvider = mock(MockWorkerApiKeyProvider.class);
        when(apiKeyProvider.hasApiKey()).thenReturn(false);
        JobWorkerProperties properties = new JobWorkerProperties(
                new JobWorkerProperties.Submit(Duration.ofSeconds(5), 10, 1, Duration.ofMinutes(1)),
                new JobWorkerProperties.Poll(Duration.ofSeconds(10), 20, 1, Duration.ofMinutes(1)),
                new JobWorkerProperties.Recovery(Duration.ofSeconds(30))
        );
        JobPollWorker worker = new JobPollWorker(
                Runnable::run,
                properties,
                jobService,
                client,
                apiKeyProvider
        );

        worker.pollProcessingJobs();

        verifyNoInteractions(jobService, client);
    }

    @Test
    @DisplayName("Mock Worker가 PROCESSING을 반환하면 poll lease를 유지하고 recovery에 맡긴다")
    void pollProcessingJobs_keepsPollLease_whenWorkerIsStillProcessing() {
        JobService jobService = mock(JobService.class);
        MockWorkerClient client = mock(MockWorkerClient.class);
        MockWorkerApiKeyProvider apiKeyProvider = mock(MockWorkerApiKeyProvider.class);
        Job job = processingJob();
        JobWorkerProperties properties = properties();
        when(apiKeyProvider.hasApiKey()).thenReturn(true);
        when(apiKeyProvider.getApiKey()).thenReturn("mock-api-key");
        when(jobService.claimJobsForPoll(20, Duration.ofMinutes(1))).thenReturn(List.of(job));
        when(client.getStatus("worker-1", "mock-api-key")).thenReturn(new MockWorkerPayloads.ProcessStatusResponse(
                "worker-1",
                MockWorkerJobStatus.PROCESSING,
                null
        ));

        JobPollWorker worker = new JobPollWorker(Runnable::run, properties, jobService, client, apiKeyProvider);

        worker.pollProcessingJobs();

        verify(jobService).claimJobsForPoll(20, Duration.ofMinutes(1));
        verify(client).getStatus("worker-1", "mock-api-key");
        verifyNoMoreInteractions(jobService);
    }

    @Test
    @DisplayName("Mock Worker가 COMPLETED를 반환하면 Job을 COMPLETED로 종료한다")
    void pollProcessingJobs_marksCompleted_whenWorkerCompleted() {
        JobService jobService = mock(JobService.class);
        MockWorkerClient client = mock(MockWorkerClient.class);
        MockWorkerApiKeyProvider apiKeyProvider = mock(MockWorkerApiKeyProvider.class);
        Job job = processingJob();
        JobWorkerProperties properties = properties();
        when(apiKeyProvider.hasApiKey()).thenReturn(true);
        when(apiKeyProvider.getApiKey()).thenReturn("mock-api-key");
        when(jobService.claimJobsForPoll(20, Duration.ofMinutes(1))).thenReturn(List.of(job));
        when(client.getStatus("worker-1", "mock-api-key")).thenReturn(new MockWorkerPayloads.ProcessStatusResponse(
                "worker-1",
                MockWorkerJobStatus.COMPLETED,
                "processed result"
        ));
        when(jobService.markPollCompleted(job.getId(), "processed result")).thenReturn(true);

        JobPollWorker worker = new JobPollWorker(Runnable::run, properties, jobService, client, apiKeyProvider);

        worker.pollProcessingJobs();

        verify(jobService).markPollCompleted(job.getId(), "processed result");
    }

    @Test
    @DisplayName("Mock Worker가 FAILED를 반환하면 Job을 FAILED로 종료한다")
    void pollProcessingJobs_marksFailed_whenWorkerFailed() {
        JobService jobService = mock(JobService.class);
        MockWorkerClient client = mock(MockWorkerClient.class);
        MockWorkerApiKeyProvider apiKeyProvider = mock(MockWorkerApiKeyProvider.class);
        Job job = processingJob();
        JobWorkerProperties properties = properties();
        when(apiKeyProvider.hasApiKey()).thenReturn(true);
        when(apiKeyProvider.getApiKey()).thenReturn("mock-api-key");
        when(jobService.claimJobsForPoll(20, Duration.ofMinutes(1))).thenReturn(List.of(job));
        when(client.getStatus("worker-1", "mock-api-key")).thenReturn(new MockWorkerPayloads.ProcessStatusResponse(
                "worker-1",
                MockWorkerJobStatus.FAILED,
                "worker failed"
        ));
        when(jobService.markPollFailed(job.getId(), "worker failed")).thenReturn(true);

        JobPollWorker worker = new JobPollWorker(Runnable::run, properties, jobService, client, apiKeyProvider);

        worker.pollProcessingJobs();

        verify(jobService).markPollFailed(job.getId(), "worker failed");
    }

    @Test
    @DisplayName("Mock Worker poll 중 429가 발생하면 poll lease를 유지하고 recovery에 맡긴다")
    void pollProcessingJobs_keepsPollLease_whenRateLimited() {
        JobService jobService = mock(JobService.class);
        MockWorkerClient client = mock(MockWorkerClient.class);
        MockWorkerApiKeyProvider apiKeyProvider = mock(MockWorkerApiKeyProvider.class);
        Job job = processingJob();
        JobWorkerProperties properties = properties();
        when(apiKeyProvider.hasApiKey()).thenReturn(true);
        when(apiKeyProvider.getApiKey()).thenReturn("mock-api-key");
        when(jobService.claimJobsForPoll(20, Duration.ofMinutes(1))).thenReturn(List.of(job));
        when(client.getStatus("worker-1", "mock-api-key")).thenThrow(new MockWorkerHttpException(
                429,
                new MockWorkerErrorPayloads.ErrorResponse("rate limited"),
                null
        ));

        JobPollWorker worker = new JobPollWorker(Runnable::run, properties, jobService, client, apiKeyProvider);

        worker.pollProcessingJobs();

        verify(jobService).claimJobsForPoll(20, Duration.ofMinutes(1));
        verify(client).getStatus("worker-1", "mock-api-key");
        verifyNoMoreInteractions(jobService);
    }

    @Test
    @DisplayName("Mock Worker poll 중 5xx가 발생하면 poll lease를 유지하고 recovery에 맡긴다")
    void pollProcessingJobs_keepsPollLease_whenServerErrorOccurs() {
        JobService jobService = mock(JobService.class);
        MockWorkerClient client = mock(MockWorkerClient.class);
        MockWorkerApiKeyProvider apiKeyProvider = mock(MockWorkerApiKeyProvider.class);
        Job job = processingJob();
        JobWorkerProperties properties = properties();
        when(apiKeyProvider.hasApiKey()).thenReturn(true);
        when(apiKeyProvider.getApiKey()).thenReturn("mock-api-key");
        when(jobService.claimJobsForPoll(20, Duration.ofMinutes(1))).thenReturn(List.of(job));
        when(client.getStatus("worker-1", "mock-api-key")).thenThrow(new MockWorkerHttpException(
                503,
                new MockWorkerErrorPayloads.ErrorResponse("unavailable"),
                null
        ));

        JobPollWorker worker = new JobPollWorker(Runnable::run, properties, jobService, client, apiKeyProvider);

        worker.pollProcessingJobs();

        verify(jobService).claimJobsForPoll(20, Duration.ofMinutes(1));
        verify(client).getStatus("worker-1", "mock-api-key");
        verifyNoMoreInteractions(jobService);
    }

    @Test
    @DisplayName("Mock Worker poll 중 네트워크 오류가 발생하면 poll lease를 유지하고 recovery에 맡긴다")
    void pollProcessingJobs_keepsPollLease_whenNetworkErrorOccurs() {
        JobService jobService = mock(JobService.class);
        MockWorkerClient client = mock(MockWorkerClient.class);
        MockWorkerApiKeyProvider apiKeyProvider = mock(MockWorkerApiKeyProvider.class);
        Job job = processingJob();
        JobWorkerProperties properties = properties();
        when(apiKeyProvider.hasApiKey()).thenReturn(true);
        when(apiKeyProvider.getApiKey()).thenReturn("mock-api-key");
        when(jobService.claimJobsForPoll(20, Duration.ofMinutes(1))).thenReturn(List.of(job));
        when(client.getStatus("worker-1", "mock-api-key")).thenThrow(new MockWorkerNetworkException(new RuntimeException("timeout")));

        JobPollWorker worker = new JobPollWorker(Runnable::run, properties, jobService, client, apiKeyProvider);

        worker.pollProcessingJobs();

        verify(jobService).claimJobsForPoll(20, Duration.ofMinutes(1));
        verify(client).getStatus("worker-1", "mock-api-key");
        verifyNoMoreInteractions(jobService);
    }

    @Test
    @DisplayName("Mock Worker poll 응답을 해석할 수 없으면 poll lease를 유지하고 recovery에 맡긴다")
    void pollProcessingJobs_keepsPollLease_whenInvalidResponseOccurs() {
        JobService jobService = mock(JobService.class);
        MockWorkerClient client = mock(MockWorkerClient.class);
        MockWorkerApiKeyProvider apiKeyProvider = mock(MockWorkerApiKeyProvider.class);
        Job job = processingJob();
        JobWorkerProperties properties = properties();
        when(apiKeyProvider.hasApiKey()).thenReturn(true);
        when(apiKeyProvider.getApiKey()).thenReturn("mock-api-key");
        when(jobService.claimJobsForPoll(20, Duration.ofMinutes(1))).thenReturn(List.of(job));
        when(client.getStatus("worker-1", "mock-api-key")).thenThrow(new MockWorkerInvalidResponseException("invalid response"));

        JobPollWorker worker = new JobPollWorker(Runnable::run, properties, jobService, client, apiKeyProvider);

        worker.pollProcessingJobs();

        verify(jobService).claimJobsForPoll(20, Duration.ofMinutes(1));
        verify(client).getStatus("worker-1", "mock-api-key");
        verifyNoMoreInteractions(jobService);
    }

    @Test
    @DisplayName("Mock Worker poll 중 재시도 불가능한 4xx가 발생하면 Job을 FAILED로 종료한다")
    void pollProcessingJobs_marksFailed_whenNonRetryableClientErrorOccurs() {
        JobService jobService = mock(JobService.class);
        MockWorkerClient client = mock(MockWorkerClient.class);
        MockWorkerApiKeyProvider apiKeyProvider = mock(MockWorkerApiKeyProvider.class);
        Job job = processingJob();
        JobWorkerProperties properties = properties();
        when(apiKeyProvider.hasApiKey()).thenReturn(true);
        when(apiKeyProvider.getApiKey()).thenReturn("mock-api-key");
        when(jobService.claimJobsForPoll(20, Duration.ofMinutes(1))).thenReturn(List.of(job));
        when(client.getStatus("worker-1", "mock-api-key")).thenThrow(new MockWorkerHttpException(
                404,
                new MockWorkerErrorPayloads.ErrorResponse("not found"),
                null
        ));
        when(jobService.markPollFailed(job.getId(), "Mock Worker request failed. status=404, detail=not found")).thenReturn(true);

        JobPollWorker worker = new JobPollWorker(Runnable::run, properties, jobService, client, apiKeyProvider);

        worker.pollProcessingJobs();

        verify(jobService).markPollFailed(job.getId(), "Mock Worker request failed. status=404, detail=not found");
    }

    @Test
    @DisplayName("여러 Job 중 하나가 일시 실패해도 나머지 Job 처리는 계속한다")
    void pollProcessingJobs_continuesOtherJobs_whenOneJobIsDeferred() {
        JobService jobService = mock(JobService.class);
        MockWorkerClient client = mock(MockWorkerClient.class);
        MockWorkerApiKeyProvider apiKeyProvider = mock(MockWorkerApiKeyProvider.class);
        Job deferred = processingJob("key-1", "worker-1");
        Job completed = processingJob("key-2", "worker-2");
        JobWorkerProperties properties = new JobWorkerProperties(
                new JobWorkerProperties.Submit(Duration.ofSeconds(5), 10, 1, Duration.ofMinutes(1)),
                new JobWorkerProperties.Poll(Duration.ofSeconds(10), 20, 2, Duration.ofMinutes(1)),
                new JobWorkerProperties.Recovery(Duration.ofSeconds(30))
        );
        when(apiKeyProvider.hasApiKey()).thenReturn(true);
        when(apiKeyProvider.getApiKey()).thenReturn("mock-api-key");
        when(jobService.claimJobsForPoll(20, Duration.ofMinutes(1))).thenReturn(List.of(deferred, completed));
        when(client.getStatus("worker-1", "mock-api-key")).thenThrow(new MockWorkerNetworkException(new RuntimeException("timeout")));
        when(client.getStatus("worker-2", "mock-api-key")).thenReturn(new MockWorkerPayloads.ProcessStatusResponse(
                "worker-2",
                MockWorkerJobStatus.COMPLETED,
                "done"
        ));
        when(jobService.markPollCompleted(completed.getId(), "done")).thenReturn(true);

        JobPollWorker worker = new JobPollWorker(Runnable::run, properties, jobService, client, apiKeyProvider);

        worker.pollProcessingJobs();

        verify(client).getStatus("worker-1", "mock-api-key");
        verify(client).getStatus("worker-2", "mock-api-key");
        verify(jobService).markPollCompleted(completed.getId(), "done");
        verify(jobService, never()).markPollFailed(deferred.getId(), "timeout");
    }

    private Job processingJob() {
        return processingJob("key-1", "worker-1");
    }

    private Job processingJob(String key, String workerJobId) {
        Job job = new Job(key, "https://example.com/" + key + ".png");
        ReflectionTestUtils.setField(job, "status", JobStatus.PROCESSING);
        ReflectionTestUtils.setField(job, "workerJobId", workerJobId);
        return job;
    }

    private JobWorkerProperties properties() {
        return new JobWorkerProperties(
                new JobWorkerProperties.Submit(Duration.ofSeconds(5), 10, 1, Duration.ofMinutes(1)),
                new JobWorkerProperties.Poll(Duration.ofSeconds(10), 20, 1, Duration.ofMinutes(1)),
                new JobWorkerProperties.Recovery(Duration.ofSeconds(30))
        );
    }
}
