package com.baklibra26.mockworker;

import com.baklibra26.common.configuration.CandidateProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MockWorkerApiKeyProvider {

    private volatile String apiKey;

    private final MockWorkerClient mockWorkerClient;
    private final CandidateProperty candidateProperty;

    public boolean hasApiKey() {
        return apiKey != null;
    }

    public String getApiKey() {
        if (apiKey == null) {
            throw new IllegalStateException("Mock Worker API key is not ready");
        }

        return apiKey;
    }

    public synchronized void prepare() {
        if (apiKey != null) {
            return;
        }

        this.apiKey = mockWorkerClient.issueApiKey(
                candidateProperty.name(),
                candidateProperty.email()
        );
    }
}
