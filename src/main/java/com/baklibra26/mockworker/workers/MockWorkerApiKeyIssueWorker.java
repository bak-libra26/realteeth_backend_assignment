package com.baklibra26.mockworker.workers;

import com.baklibra26.mockworker.MockWorkerApiKeyProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MockWorkerApiKeyIssueWorker {

    private final MockWorkerApiKeyProvider apiKeyProvider;

    @Scheduled(fixedDelayString = "#{@environment.getProperty('img-proc.mock-worker.api-key-issue.interval', T(java.time.Duration)).toMillis()}")
    public void issueApiKeyIfNecessary() {
        if (apiKeyProvider.hasApiKey()) {
            return;
        }

        try {
            apiKeyProvider.prepare();
            log.info("Mock Worker API key issued. job schedulers are ready");
        } catch (RuntimeException e) {
            log.warn("Mock Worker API key issue failed. job schedulers will wait and retry. detail={}", e.getMessage());
        }
    }
}
