package com.baklibra26.job.controllers;

import com.baklibra26.job.Job;
import com.baklibra26.job.JobPayloads.CreateJobRequest;
import com.baklibra26.job.JobPayloads.JobPageResponse;
import com.baklibra26.job.JobPayloads.JobResponse;
import com.baklibra26.job.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobControllerImpl implements JobController {

    private final JobService jobService;

    @Override
    public ResponseEntity<JobResponse> createJob(
            String idempotencyKey,
            CreateJobRequest request
    ) {
        Job job = jobService.createJob(idempotencyKey, request.imageUrl());
        log.info("job create request handled. jobId={}, status={}", job.getId(), job.getStatus());
        return ResponseEntity.accepted().body(JobResponse.from(job));
    }

    @Override
    public JobResponse getJob(UUID jobId) {
        return JobResponse.from(jobService.getJobById(jobId));
    }

    @Override
    public JobPageResponse getJobs(
            int page,
            int size
    ) {
        return JobPageResponse.from(jobService.getJobs(page, size));
    }
}
