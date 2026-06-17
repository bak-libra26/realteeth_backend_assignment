package com.baklibra26.common.configuration;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "img-proc.cors")
public record CorsProperties(
        @NotEmpty(message = "img-proc.cors.allowed-origins is required")
        List<String> allowedOrigins,

        @NotEmpty(message = "img-proc.cors.allowed-methods is required")
        List<String> allowedMethods,

        @NotEmpty(message = "img-proc.cors.allowed-headers is required")
        List<String> allowedHeaders,

        @NotNull(message = "img-proc.cors.max-age is required")
        Duration maxAge
) {
}
