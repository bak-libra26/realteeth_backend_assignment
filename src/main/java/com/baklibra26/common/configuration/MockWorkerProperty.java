package com.baklibra26.common.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "img-proc.mock-worker")
public record MockWorkerProperty(
    @NotBlank(message = "img-proc.mock-worker.base-url is required")
    @URL String baseUrl,
    Http http,
    ApiKeyIssue apiKeyIssue
) {

    public record Http(
        @NotNull(message = "img-proc.mock-worker.http.connect-timeout is required")
        Duration connectTimeout,

        @NotNull(message = "img-proc.mock-worker.http.read-timeout is required")
        Duration readTimeout
    ) {}

    public record ApiKeyIssue(
        @NotNull(message = "img-proc.mock-worker.api-key-issue.interval is required")
        Duration interval
    ) {}
}
