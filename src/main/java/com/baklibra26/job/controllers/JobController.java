package com.baklibra26.job.controllers;

import com.baklibra26.common.exception.GlobalExceptionHandler.ErrorResponse;
import com.baklibra26.job.JobPayloads.CreateJobRequest;
import com.baklibra26.job.JobPayloads.JobPageResponse;
import com.baklibra26.job.JobPayloads.JobResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@Validated
@Tag(name = "Jobs", description = "Asynchronous image processing jobs")
public interface JobController {

    @Operation(
            summary = "Create image processing job",
            description = "Creates a Job and returns immediately. The image is processed asynchronously by Mock Worker.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = CreateJobRequest.class),
                            examples = @ExampleObject(value = """
                                    {"imageUrl":"https://example.com/image.png"}
                                    """)
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Job accepted"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code":"BAD_REQUEST","message":"imageUrl: imageUrl must be a valid https URL"}
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Idempotency key conflict",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code":"IDEMPOTENCY_CONFLICT","message":"Idempotency key conflict. idempotencyKey=sample-key-1"}
                                    """)
                    )
            )
    })
    @PostMapping
    ResponseEntity<JobResponse> createJob(
            @Parameter(description = "Idempotency key for duplicate request handling", required = true)
            @RequestHeader("Idempotency-Key")
            @NotBlank(message = "Idempotency-Key is required")
            @Size(max = 128, message = "Idempotency-Key must be at most 128 characters")
            String idempotencyKey,

            @Valid @RequestBody CreateJobRequest request
    );

    @Operation(summary = "Get job")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Job found"),
            @ApiResponse(
                    responseCode = "404",
                    description = "Job not found",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code":"NOT_FOUND","message":"Job not found. jobId=00000000-0000-0000-0000-000000000000"}
                                    """)
                    )
            )
    })
    @GetMapping("/{jobId}")
    JobResponse getJob(
            @Parameter(description = "Job ID", required = true)
            @PathVariable UUID jobId
    );

    @Operation(summary = "List jobs")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Jobs listed"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid paging parameter",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {"code":"BAD_REQUEST","message":"getJobs.size: size must be at most 100"}
                                    """)
                    )
            )
    })
    @GetMapping
    JobPageResponse getJobs(
            @Parameter(description = "Zero-based page number")
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page must be at least 0")
            int page,

            @Parameter(description = "Page size. Maximum is 100.")
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must be at most 100")
            int size
    );
}
