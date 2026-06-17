package com.baklibra26.job;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public final class JobPayloads {

    private JobPayloads() {
    }

    public record CreateJobRequest(
            @NotBlank(message = "imageUrl is required")
            @Size(max = 2048, message = "imageUrl must be at most 2048 characters")
            @URL(protocol = "https", message = "imageUrl must be a valid https URL")
            String imageUrl
    ) {}

    public record JobResponse(
            UUID jobId,
            String imageUrl,
            JobStatus status,
            String result,
            String createdAt
    ) {
        public static JobResponse from(Job job) {
            return new JobResponse(
                    job.getId(),
                    job.getImageUrl(),
                    job.getStatus(),
                    job.getResult(),
                    job.getCreatedAt() == null ? null : job.getCreatedAt().toString()
            );
        }
    }

    public record JobPageResponse(
            List<JobResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext
    ) {
        public static JobPageResponse from(Page<Job> page) {
            return new JobPageResponse(
                    page.getContent().stream()
                            .map(JobResponse::from)
                            .toList(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages(),
                    page.hasNext()
            );
        }
    }
}
