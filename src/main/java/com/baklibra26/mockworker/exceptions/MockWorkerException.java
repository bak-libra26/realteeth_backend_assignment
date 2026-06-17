package com.baklibra26.mockworker.exceptions;

public class MockWorkerException extends RuntimeException {


    public MockWorkerException(String msg) {
        super(msg);
    }

    public MockWorkerException(String msg, Throwable cause) {
        super(msg, cause);
    }

}
