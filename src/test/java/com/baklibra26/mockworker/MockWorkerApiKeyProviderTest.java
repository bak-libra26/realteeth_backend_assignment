package com.baklibra26.mockworker;

import com.baklibra26.common.configuration.CandidateProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MockWorkerApiKeyProvider")
class MockWorkerApiKeyProviderTest {

    @Test
    @DisplayName("API key를 발급받고 이후 조회에서는 캐시된 값을 반환한다")
    void prepare_issuesAndGetApiKeyReturnsCachedApiKey() {
        // given
        MockWorkerClient client = mock(MockWorkerClient.class);
        MockWorkerApiKeyProvider provider = new MockWorkerApiKeyProvider(
                client,
                new CandidateProperty("candidate", "candidate@example.com")
        );
        when(client.issueApiKey("candidate", "candidate@example.com"))
                .thenReturn("mock-api-key");

        // when & then
        provider.prepare();
        assertThat(provider.getApiKey()).isEqualTo("mock-api-key");
        provider.prepare();

        verify(client).issueApiKey("candidate", "candidate@example.com");
    }

    @Test
    @DisplayName("동시에 API key 준비를 시도해도 발급 요청은 한 번만 보낸다")
    void prepare_issuesApiKeyOnlyOnce_whenCalledConcurrently() throws Exception {
        // given
        MockWorkerClient client = mock(MockWorkerClient.class);
        MockWorkerApiKeyProvider provider = new MockWorkerApiKeyProvider(
                client,
                new CandidateProperty("candidate", "candidate@example.com")
        );
        when(client.issueApiKey("candidate", "candidate@example.com"))
                .thenAnswer(invocation -> {
                    Thread.sleep(50);
                    return "mock-api-key";
                });
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);

        // when
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                ready.countDown();
                start.await();
                provider.prepare();
                return null;
            });
        }
        assertThat(ready.await(1, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();

        // then
        assertThat(provider.getApiKey()).isEqualTo("mock-api-key");
        verify(client, times(1)).issueApiKey("candidate", "candidate@example.com");
    }

    @Test
    @DisplayName("API key 발급에 실패하면 캐시하지 않는다")
    void prepare_doesNotCacheApiKey_whenIssueFails() {
        // given
        MockWorkerClient client = mock(MockWorkerClient.class);
        MockWorkerApiKeyProvider provider = new MockWorkerApiKeyProvider(
                client,
                new CandidateProperty("candidate", "candidate@example.com")
        );
        when(client.issueApiKey("candidate", "candidate@example.com"))
                .thenThrow(new RuntimeException("issue failed"));

        // when & then
        assertThatThrownBy(provider::prepare)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("issue failed");
        assertThat(provider.hasApiKey()).isFalse();
    }

    @Test
    @DisplayName("API key가 준비되지 않았으면 조회할 수 없다")
    void getApiKey_throws_whenApiKeyIsNotReady() {
        MockWorkerApiKeyProvider provider = new MockWorkerApiKeyProvider(
                mock(MockWorkerClient.class),
                new CandidateProperty("candidate", "candidate@example.com")
        );

        assertThatThrownBy(provider::getApiKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API key is not ready");
    }
}
