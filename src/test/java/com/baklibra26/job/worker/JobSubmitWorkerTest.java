package com.baklibra26.job.workers;

import com.baklibra26.common.configuration.JobWorkerProperties;
import com.baklibra26.job.Job;
import com.baklibra26.job.JobService;
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

import java.time.Duration;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@DisplayName("JobSubmitWorker")
class JobSubmitWorkerTest {

    @Test
    @DisplayName("Mock Worker API key가 준비되지 않았으면 pending Job을 선점하지 않는다")
    void submitPendingJobs_skipsClaim_whenApiKeyIsNotReady() {
        JobService jobService = mock(JobService.class);
        MockWorkerClient client = mock(MockWorkerClient.class);
        MockWorkerApiKeyProvider apiKeyProvider = mock(MockWorkerApiKeyProvider.class);
        when(apiKeyProvider.hasApiKey()).thenReturn(false);
        JobWorkerProperties properties = new JobWorkerProperties(
                new JobWorkerProperties.Submit(Duration.ofSeconds(5), 10, 1, Duration.ofMinutes(1)),
                new JobWorkerProperties.Poll(Duration.ofSeconds(10), 20, 1, Duration.ofMinutes(1)),
                new JobWorkerProperties.Recovery(Duration.ofSeconds(30))
        );
        JobSubmitWorker worker = new JobSubmitWorker(
                Runnable::run,
                properties,
                jobService,
                client,
                apiKeyProvider
        );

        worker.submitPendingJobs();

        verifyNoInteractions(jobService, client);
    }

    @Test
    @DisplayName("Mock Worker가 5xx를 반환하면 상태를 확정하지 않고 recovery에 맡긴다")
    void submitPendingJobs_defersToRecovery_whenServerErrorOccurs() {
        JobService jobService = mock(JobService.class);
        MockWorkerClient client = mock(MockWorkerClient.class);
        MockWorkerApiKeyProvider apiKeyProvider = mock(MockWorkerApiKeyProvider.class);
        Job job = new Job("key-1", "https://example.com/image.png");
        JobWorkerProperties properties = properties();
        when(apiKeyProvider.hasApiKey()).thenReturn(true);
        when(apiKeyProvider.getApiKey()).thenReturn("mock-api-key");
        when(jobService.claimJobsForSubmit(10, Duration.ofMinutes(1))).thenReturn(List.of(job));
        when(client.submit(job, "mock-api-key")).thenThrow(new MockWorkerHttpException(
                503,
                new MockWorkerErrorPayloads.ErrorResponse("unavailable"),
                null
        ));

        JobSubmitWorker worker = new JobSubmitWorker(Runnable::run, properties, jobService, client, apiKeyProvider);

        worker.submitPendingJobs();

        verify(jobService).claimJobsForSubmit(10, Duration.ofMinutes(1));
        verify(client).submit(job, "mock-api-key");
        verify(jobService, never()).markSubmitFailed(job.getId(), "Mock Worker request failed. status=503, detail=unavailable");
        verifyNoMoreInteractions(jobService);
    }

    @Test
    @DisplayName("Mock Worker 제출에 성공하면 Job을 PROCESSING으로 변경하고 첫 poll을 예약한다")
    void submitPendingJobs_marksProcessing_whenSubmitSucceeds() {
        JobService jobService = mock(JobService.class);
        MockWorkerClient client = mock(MockWorkerClient.class);
        MockWorkerApiKeyProvider apiKeyProvider = mock(MockWorkerApiKeyProvider.class);
        Job job = new Job("key-1", "https://example.com/image.png");
        JobWorkerProperties properties = properties();
        when(apiKeyProvider.hasApiKey()).thenReturn(true);
        when(apiKeyProvider.getApiKey()).thenReturn("mock-api-key");
        when(jobService.claimJobsForSubmit(10, Duration.ofMinutes(1))).thenReturn(List.of(job));
        when(client.submit(job, "mock-api-key")).thenReturn(new MockWorkerPayloads.ProcessStartResponse("worker-1", MockWorkerJobStatus.PROCESSING));
        when(jobService.markSubmitSucceeded(job.getId(), "worker-1")).thenReturn(true);

        JobSubmitWorker worker = new JobSubmitWorker(Runnable::run, properties, jobService, client, apiKeyProvider);

        worker.submitPendingJobs();

        verify(client).submit(job, "mock-api-key");
        verify(jobService).markSubmitSucceeded(job.getId(), "worker-1");
    }

    @Test
    @DisplayName("Mock Worker가 429를 반환하면 상태를 확정하지 않고 recovery에 맡긴다")
    void submitPendingJobs_defersToRecovery_whenRateLimited() {
        JobService jobService = mock(JobService.class);
        MockWorkerClient client = mock(MockWorkerClient.class);
        MockWorkerApiKeyProvider apiKeyProvider = mock(MockWorkerApiKeyProvider.class);
        Job job = new Job("key-1", "https://example.com/image.png");
        JobWorkerProperties properties = properties();
        when(apiKeyProvider.hasApiKey()).thenReturn(true);
        when(apiKeyProvider.getApiKey()).thenReturn("mock-api-key");
        when(jobService.claimJobsForSubmit(10, Duration.ofMinutes(1))).thenReturn(List.of(job));
        when(client.submit(job, "mock-api-key")).thenThrow(new MockWorkerHttpException(
                429,
                new MockWorkerErrorPayloads.ErrorResponse("rate limited"),
                null
        ));

        JobSubmitWorker worker = new JobSubmitWorker(Runnable::run, properties, jobService, client, apiKeyProvider);

        worker.submitPendingJobs();

        verify(jobService).claimJobsForSubmit(10, Duration.ofMinutes(1));
        verify(client).submit(job, "mock-api-key");
        verifyNoMoreInteractions(jobService);
    }

    @Test
    @DisplayName("Mock Worker 제출 중 네트워크 오류가 발생하면 상태를 확정하지 않고 recovery에 맡긴다")
    void submitPendingJobs_defersToRecovery_whenNetworkErrorOccurs() {
        JobService jobService = mock(JobService.class);
        MockWorkerClient client = mock(MockWorkerClient.class);
        MockWorkerApiKeyProvider apiKeyProvider = mock(MockWorkerApiKeyProvider.class);
        Job job = new Job("key-1", "https://example.com/image.png");
        JobWorkerProperties properties = properties();
        when(apiKeyProvider.hasApiKey()).thenReturn(true);
        when(apiKeyProvider.getApiKey()).thenReturn("mock-api-key");
        when(jobService.claimJobsForSubmit(10, Duration.ofMinutes(1))).thenReturn(List.of(job));
        when(client.submit(job, "mock-api-key")).thenThrow(new MockWorkerNetworkException(new RuntimeException("timeout")));

        JobSubmitWorker worker = new JobSubmitWorker(Runnable::run, properties, jobService, client, apiKeyProvider);

        worker.submitPendingJobs();

        verify(jobService).claimJobsForSubmit(10, Duration.ofMinutes(1));
        verify(client).submit(job, "mock-api-key");
        verifyNoMoreInteractions(jobService);
    }

    @Test
    @DisplayName("Mock Worker 응답을 해석할 수 없으면 상태를 확정하지 않고 recovery에 맡긴다")
    void submitPendingJobs_defersToRecovery_whenInvalidResponseOccurs() {
        JobService jobService = mock(JobService.class);
        MockWorkerClient client = mock(MockWorkerClient.class);
        MockWorkerApiKeyProvider apiKeyProvider = mock(MockWorkerApiKeyProvider.class);
        Job job = new Job("key-1", "https://example.com/image.png");
        JobWorkerProperties properties = properties();
        when(apiKeyProvider.hasApiKey()).thenReturn(true);
        when(apiKeyProvider.getApiKey()).thenReturn("mock-api-key");
        when(jobService.claimJobsForSubmit(10, Duration.ofMinutes(1))).thenReturn(List.of(job));
        when(client.submit(job, "mock-api-key")).thenThrow(new MockWorkerInvalidResponseException("invalid response"));

        JobSubmitWorker worker = new JobSubmitWorker(Runnable::run, properties, jobService, client, apiKeyProvider);

        worker.submitPendingJobs();

        verify(jobService).claimJobsForSubmit(10, Duration.ofMinutes(1));
        verify(client).submit(job, "mock-api-key");
        verifyNoMoreInteractions(jobService);
    }

    @Test
    @DisplayName("Mock Worker가 재시도 불가능한 4xx를 반환하면 Job을 FAILED로 종료한다")
    void submitPendingJobs_marksFailed_whenNonRetryableClientErrorOccurs() {
        JobService jobService = mock(JobService.class);
        MockWorkerClient client = mock(MockWorkerClient.class);
        MockWorkerApiKeyProvider apiKeyProvider = mock(MockWorkerApiKeyProvider.class);
        Job job = new Job("key-1", "https://example.com/image.png");
        JobWorkerProperties properties = properties();
        when(apiKeyProvider.hasApiKey()).thenReturn(true);
        when(apiKeyProvider.getApiKey()).thenReturn("mock-api-key");
        when(jobService.claimJobsForSubmit(10, Duration.ofMinutes(1))).thenReturn(List.of(job));
        when(client.submit(job, "mock-api-key")).thenThrow(new MockWorkerHttpException(
                400,
                new MockWorkerErrorPayloads.ErrorResponse("bad image"),
                null
        ));
        when(jobService.markSubmitFailed(job.getId(), "Mock Worker request failed. status=400, detail=bad image")).thenReturn(true);

        JobSubmitWorker worker = new JobSubmitWorker(Runnable::run, properties, jobService, client, apiKeyProvider);

        worker.submitPendingJobs();

        verify(jobService).markSubmitFailed(job.getId(), "Mock Worker request failed. status=400, detail=bad image");
    }

    @Test
    @DisplayName("여러 Job 중 하나가 일시 실패해도 나머지 Job 처리는 계속한다")
    void submitPendingJobs_continuesOtherJobs_whenOneJobIsDeferred() {
        JobService jobService = mock(JobService.class);
        MockWorkerClient client = mock(MockWorkerClient.class);
        MockWorkerApiKeyProvider apiKeyProvider = mock(MockWorkerApiKeyProvider.class);
        Job deferred = new Job("key-1", "https://example.com/deferred.png");
        Job succeeded = new Job("key-2", "https://example.com/succeeded.png");
        JobWorkerProperties properties = new JobWorkerProperties(
                new JobWorkerProperties.Submit(Duration.ofSeconds(5), 10, 2, Duration.ofMinutes(1)),
                new JobWorkerProperties.Poll(Duration.ofSeconds(10), 20, 1, Duration.ofMinutes(1)),
                new JobWorkerProperties.Recovery(Duration.ofSeconds(30))
        );
        when(apiKeyProvider.hasApiKey()).thenReturn(true);
        when(apiKeyProvider.getApiKey()).thenReturn("mock-api-key");
        when(jobService.claimJobsForSubmit(10, Duration.ofMinutes(1))).thenReturn(List.of(deferred, succeeded));
        when(client.submit(deferred, "mock-api-key")).thenThrow(new MockWorkerNetworkException(new RuntimeException("timeout")));
        when(client.submit(succeeded, "mock-api-key")).thenReturn(new MockWorkerPayloads.ProcessStartResponse("worker-2", MockWorkerJobStatus.PROCESSING));
        when(jobService.markSubmitSucceeded(succeeded.getId(), "worker-2")).thenReturn(true);

        JobSubmitWorker worker = new JobSubmitWorker(Runnable::run, properties, jobService, client, apiKeyProvider);

        worker.submitPendingJobs();

        verify(client).submit(deferred, "mock-api-key");
        verify(client).submit(succeeded, "mock-api-key");
        verify(jobService).markSubmitSucceeded(succeeded.getId(), "worker-2");
        verify(jobService, never()).markSubmitFailed(deferred.getId(), "timeout");
    }

    private JobWorkerProperties properties() {
        return new JobWorkerProperties(
                new JobWorkerProperties.Submit(Duration.ofSeconds(5), 10, 1, Duration.ofMinutes(1)),
                new JobWorkerProperties.Poll(Duration.ofSeconds(10), 20, 1, Duration.ofMinutes(1)),
                new JobWorkerProperties.Recovery(Duration.ofSeconds(30))
        );
    }
}
