package com.baklibra26.mockworker;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class MockWorkerPayloads {

    private MockWorkerPayloads() {
    }

    record IssueApiKeyRequest(
        String candidateName,
        String email
    ) {}

    record IssueApiKeyResponse(
        String apiKey
    ) {}

    public record ProcessRequest(
        String imageUrl
    ) {}

    public record ProcessStartResponse(
        @NotBlank(message = "jobId is required.")
        String jobId,
        @NotNull(message = "status is required.")
        MockWorkerJobStatus status
    ) {}

    public record ProcessStatusResponse(
        String jobId,
        MockWorkerJobStatus status,
        String result
    ) {}
}
