package com.baklibra26.job.exceptions;

import java.util.UUID;

public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(UUID jobId) {
        super("Job not found. jobId=" + jobId);
    }
}
