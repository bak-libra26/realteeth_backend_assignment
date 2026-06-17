package com.baklibra26.common.configuration;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "img-proc.candidate")
public record CandidateProperty(
    @NotBlank(message = "img-proc.candidate.name is required")
    String name,

    @NotBlank(message = "img-proc.candidate.email is required")
    @Email(message = "img-proc.candidate.email must be valid")
    String email
) {}
