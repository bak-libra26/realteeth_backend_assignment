package com.baklibra26.mockworker.exceptions;

import com.baklibra26.mockworker.MockWorkerErrorPayloads;
import lombok.Getter;

@Getter
public class MockWorkerHttpException extends MockWorkerException {


    private final int statusCode;
    private final MockWorkerErrorPayloads.ErrorBody body;

    public MockWorkerHttpException(int statusCode, MockWorkerErrorPayloads.ErrorBody body, Throwable cause) {
        super("Mock Worker request failed. status=%d, detail=%s"
                .formatted(statusCode, message(body)), cause);
        this.statusCode = statusCode;
        this.body = body == null ? new MockWorkerErrorPayloads.ErrorResponse(null) : body;
    }

    private static String message(MockWorkerErrorPayloads.ErrorBody body) {
        if (body == null) {
            return "Mock Worker request failed";
        }

        return body.message();
    }

    public boolean isRateLimited() {
        return statusCode == 429;
    }

    public boolean isServerError() {
        return statusCode >= 500 && statusCode < 600;
    }

    public boolean isClientError() {
        return statusCode >= 400 && statusCode < 500;
    }
}
