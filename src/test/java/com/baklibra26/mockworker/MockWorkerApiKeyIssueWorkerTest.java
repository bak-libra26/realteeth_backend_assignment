package com.baklibra26.mockworker;

import com.baklibra26.mockworker.workers.MockWorkerApiKeyIssueWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MockWorkerApiKeyIssueWorker")
class MockWorkerApiKeyIssueWorkerTest {

    @Test
    @DisplayName("API key가 없으면 발급을 시도하고 실패해도 다음 스케줄을 위해 예외를 삼킨다")
    void issueApiKeyIfNecessary_retriesLater_whenIssueFails() {
        MockWorkerApiKeyProvider provider = mock(MockWorkerApiKeyProvider.class);
        when(provider.hasApiKey()).thenReturn(false);
        doThrow(new RuntimeException("network error")).when(provider).prepare();

        MockWorkerApiKeyIssueWorker worker = new MockWorkerApiKeyIssueWorker(provider);

        worker.issueApiKeyIfNecessary();

        verify(provider).prepare();
    }

    @Test
    @DisplayName("API key가 이미 있으면 발급을 다시 시도하지 않는다")
    void issueApiKeyIfNecessary_skips_whenApiKeyIsReady() {
        MockWorkerApiKeyProvider provider = mock(MockWorkerApiKeyProvider.class);
        when(provider.hasApiKey()).thenReturn(true);

        MockWorkerApiKeyIssueWorker worker = new MockWorkerApiKeyIssueWorker(provider);

        worker.issueApiKeyIfNecessary();

        verify(provider, never()).prepare();
    }
}
