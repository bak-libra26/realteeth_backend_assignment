package com.baklibra26.job.exceptions;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String idempotencyKey) {
        super("Idempotency-Key was reused with a different request. key=" + idempotencyKey);
    }
}
