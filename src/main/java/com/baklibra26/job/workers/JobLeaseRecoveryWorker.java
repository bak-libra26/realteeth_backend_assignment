package com.baklibra26.job.workers;

import com.baklibra26.job.JobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JobLeaseRecoveryWorker {

    private final JobService jobService;

    public JobLeaseRecoveryWorker(JobService jobService) {
        this.jobService = jobService;
    }

    @Scheduled(fixedDelayString = "#{@environment.getProperty('img-proc.job-worker.recovery.interval', T(java.time.Duration)).toMillis()}")
    public void runLeaseRecovery() {
        int recovered = jobService.recoverExpiredLeases();
        if (recovered > 0) {
            log.warn("expired job leases recovered. count={}", recovered);
        }
    }

}
