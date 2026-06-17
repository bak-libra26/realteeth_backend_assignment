package com.baklibra26.mockworker.exceptions;

public class MockWorkerNetworkException extends MockWorkerException {

    public MockWorkerNetworkException(Throwable cause) {
        super("Mock Worker request failed by timeout or network error", cause);
    }
}
