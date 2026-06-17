package com.baklibra26.mockworker;

import com.baklibra26.mockworker.exceptions.MockWorkerException;
import com.baklibra26.mockworker.exceptions.MockWorkerHttpException;
import com.baklibra26.mockworker.exceptions.MockWorkerInvalidResponseException;
import com.baklibra26.mockworker.exceptions.MockWorkerNetworkException;
import com.baklibra26.job.Job;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import static com.baklibra26.mockworker.MockWorkerPayloads.*;

@Service
@RequiredArgsConstructor
public class MockWorkerClient {

    private final RestClient mockWorkerRestClient;

    public String issueApiKey(String candidateName, String email) throws MockWorkerException {
        IssueApiKeyResponse response = mockWorkerRestClient
                .post()
                .uri("/auth/issue-key")
                .body(new IssueApiKeyRequest(candidateName, email))
                .retrieve()
                .body(IssueApiKeyResponse.class);

        if (response == null || response.apiKey() == null || response.apiKey().isBlank()) {
            throw new MockWorkerException("Mock Worker API key issue response is empty");
        }

        return response.apiKey();
    }

    public ProcessStartResponse submit(@NotNull Job job, String apiKey) throws MockWorkerException {
        try {
            ProcessStartResponse response = mockWorkerRestClient.post()
                    .uri("/process")
                    .header("X-API-KEY", apiKey)
                    .body(new ProcessRequest(job.getImageUrl()))
                    .retrieve()
                    .body(ProcessStartResponse.class);

            validateProcessStartResponse(response);
            return response;
        } catch (ResourceAccessException e) {
            throw new MockWorkerNetworkException(e);
        } catch (RestClientResponseException e) {
            throw new MockWorkerHttpException(
                    e.getStatusCode().value(),
                    parseErrorBody(e),
                    e
            );
        } catch (RestClientException e) {
            throw new MockWorkerInvalidResponseException("Mock Worker process start response could not be parsed: " + e.getMessage());
        }
    }

    public ProcessStatusResponse getStatus(String jobId, String apiKey) {
        try {
            ProcessStatusResponse response = mockWorkerRestClient.get()
                    .uri("/process/{jobId}", jobId)
                    .header("X-API-KEY", apiKey)
                    .retrieve()
                    .body(ProcessStatusResponse.class);

            validateProcessStatusResponse(response);
            return response;
        } catch (ResourceAccessException e) {
            throw new MockWorkerNetworkException(e);
        } catch (RestClientResponseException e) {
            throw new MockWorkerHttpException(
                    e.getStatusCode().value(),
                    parseErrorBody(e),
                    e
            );
        } catch (RestClientException e) {
            throw new MockWorkerInvalidResponseException("Mock Worker process status response could not be parsed: " + e.getMessage());
        }
    }

    private void validateProcessStartResponse(ProcessStartResponse response) {
        if (response == null) {
            throw new MockWorkerInvalidResponseException(
                    "Mock Worker process start response is empty"
            );
        }
        if (response.jobId() == null || response.jobId().isBlank()) {
            throw new MockWorkerInvalidResponseException(
                    "Mock Worker process start response jobId is empty"
            );
        }
        if (response.status() == null) {
            throw new MockWorkerInvalidResponseException(
                    "Mock Worker process start response status is empty"
            );
        }
        if (response.status() != MockWorkerJobStatus.PROCESSING) {
            throw new MockWorkerInvalidResponseException(
                    "Mock Worker process start response status must be PROCESSING"
            );
        }
    }

    private void validateProcessStatusResponse(ProcessStatusResponse response) {
        if (response == null) {
            throw new MockWorkerInvalidResponseException(
                    "Mock Worker process status response is empty"
            );
        }
        if (response.status() == null) {
            throw new MockWorkerInvalidResponseException(
                    "Mock Worker process status response status is empty"
            );
        }
    }

    private MockWorkerErrorPayloads.ErrorBody parseErrorBody(RestClientResponseException e) {
        MockWorkerErrorPayloads.ErrorBody body;
        try {
            body = switch (e.getStatusCode().value()) {
                case 422 -> e.getResponseBodyAs(MockWorkerErrorPayloads.HttpValidationError.class);
                case 400, 401, 429, 500 -> e.getResponseBodyAs(MockWorkerErrorPayloads.ErrorResponse.class);
                default -> new MockWorkerErrorPayloads.ErrorResponse(e.getResponseBodyAsString());
            };
        } catch (RestClientException parseFailure) {
            body = new MockWorkerErrorPayloads.ErrorResponse(e.getResponseBodyAsString());
        }

        return body == null ? new MockWorkerErrorPayloads.ErrorResponse(e.getResponseBodyAsString()) : body;
    }
}
