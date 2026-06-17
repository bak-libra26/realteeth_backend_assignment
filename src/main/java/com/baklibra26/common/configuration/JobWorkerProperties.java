package com.baklibra26.common.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "img-proc.job-worker")
public record JobWorkerProperties(
        @Valid @NotNull(message = "img-proc.job-worker.submit is required")
        JobWorkerProperties.Submit submit,

        @Valid @NotNull(message = "img-proc.job-worker.poll is required")
        JobWorkerProperties.Poll poll,

        @Valid @NotNull(message = "img-proc.job-worker.recovery is required")
        JobWorkerProperties.Recovery recovery
) {

    public record Submit(
            @NotNull(message = "interval is required")
            Duration interval,

            @Min(value = 1, message = "batch-size must be at least 1")
            int batchSize,

            @Min(value = 1, message = "concurrency must be at least 1")
            int concurrency,

            @NotNull(message = "lease-timeout is required")
            Duration leaseTimeout
    ) {}

    public record Poll(
            @NotNull(message = "interval is required")
            Duration interval,

            @Min(value = 1, message = "batch-size must be at least 1")
            int batchSize,

            @Min(value = 1, message = "concurrency must be at least 1")
            int concurrency,

            @NotNull(message = "lease-timeout is required")
            Duration leaseTimeout
    ) {}

    public record Recovery(
            @NotNull(message = "interval is required")
            Duration interval
    ) {}
}
