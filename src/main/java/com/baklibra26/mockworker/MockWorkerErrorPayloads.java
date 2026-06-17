package com.baklibra26.mockworker;

import java.util.List;
import java.util.Map;

public final class MockWorkerErrorPayloads {

    private MockWorkerErrorPayloads() {
    }

    public sealed interface ErrorBody permits ErrorResponse, HttpValidationError {
        String message();
    }

    public record ErrorResponse(
            String detail
    ) implements ErrorBody {

        @Override
        public String message() {
            if (detail == null || detail.isBlank()) {
                return "Mock Worker request failed";
            }
            return detail;
        }
    }

    public record ValidationError(
            List<Object> loc,
            String msg,
            String type,
            Object input,
            Map<String, Object> ctx
    ) {
    }

    public record HttpValidationError(
            List<ValidationError> detail
    ) implements ErrorBody {

        @Override
        public String message() {
            if (detail == null || detail.isEmpty() || detail.get(0).msg() == null) {
                return "Mock Worker validation failed";
            }

            return detail.get(0).msg();
        }
    }
}
