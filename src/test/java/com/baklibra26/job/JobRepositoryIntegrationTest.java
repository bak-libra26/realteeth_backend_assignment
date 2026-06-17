package com.baklibra26.job;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@Import(JobService.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("JobRepository integration")
class JobRepositoryIntegrationTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.sql.init.mode", () -> "always");
    }

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobService jobService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("submit claim 대상은 lease가 없는 PENDING Job뿐이다")
    void findSubmitCandidatesForUpdateSkipLocked_returnsOnlyUnleasedPendingJobs() {
        Instant now = Instant.now();
        Job ready = saveJob("ready", "https://example.com/ready.png");
        saveJob("leased", "https://example.com/leased.png", job ->
                job.leaseForSubmit(now.plusSeconds(60))
        );
        saveJob("processing", "https://example.com/processing.png", job -> {
            ReflectionTestUtils.setField(job, "status", JobStatus.PROCESSING);
            ReflectionTestUtils.setField(job, "workerJobId", "worker-1");
        });

        List<Job> jobs = jobRepository.findSubmitCandidatesForUpdateSkipLocked(10);

        assertThat(jobs)
                .extracting(Job::getId)
                .containsExactly(ready.getId());
    }

    @Test
    @DisplayName("poll claim 대상은 lease가 없는 PROCESSING Job뿐이다")
    void findPollCandidatesForUpdateSkipLocked_returnsOnlyUnleasedProcessingJobs() {
        Instant now = Instant.now();
        Job ready = saveProcessingJob("ready", "worker-ready");
        saveJob("leased", "https://example.com/leased.png", job -> {
            ReflectionTestUtils.setField(job, "status", JobStatus.PROCESSING);
            ReflectionTestUtils.setField(job, "workerJobId", "worker-leased");
            job.leaseForPoll(now.plusSeconds(60));
        });
        saveJob("pending", "https://example.com/pending.png");

        List<Job> jobs = jobRepository.findPollCandidatesForUpdateSkipLocked(10);

        assertThat(jobs)
                .extracting(Job::getId)
                .containsExactly(ready.getId());
    }

    @Test
    @DisplayName("submit claim은 조회한 managed Job의 lease 변경을 DB에 반영한다")
    void claimJobsForSubmit_persistsSubmitLeaseByDirtyChecking() {
        Job pending = saveJob("claim-submit", "https://example.com/claim-submit.png");

        List<Job> claimed = jobService.claimJobsForSubmit(10, Duration.ofMinutes(1));
        flushAndClearAsRestart();

        Job reloaded = jobRepository.findById(pending.getId()).orElseThrow();
        assertThat(claimed)
                .extracting(Job::getId)
                .containsExactly(pending.getId());
        assertThat(reloaded.getLeaseType()).isEqualTo(JobLeaseType.SUBMIT);
        assertThat(reloaded.getLeasedUntil()).isNotNull();
    }

    @Test
    @DisplayName("poll claim은 조회한 managed Job의 lease 변경을 DB에 반영한다")
    void claimJobsForPoll_persistsPollLeaseByDirtyChecking() {
        Job processing = saveProcessingJob("claim-poll", "worker-claim-poll");

        List<Job> claimed = jobService.claimJobsForPoll(10, Duration.ofMinutes(1));
        flushAndClearAsRestart();

        Job reloaded = jobRepository.findById(processing.getId()).orElseThrow();
        assertThat(claimed)
                .extracting(Job::getId)
                .containsExactly(processing.getId());
        assertThat(reloaded.getLeaseType()).isEqualTo(JobLeaseType.POLL);
        assertThat(reloaded.getLeasedUntil()).isNotNull();
    }

    @Test
    @DisplayName("markSubmitSucceeded은 PENDING/SUBMIT lease Job만 PROCESSING으로 변경한다")
    void markSubmitSucceeded_updatesOnlyPendingSubmitLeaseJob() {
        Instant now = Instant.now();
        Job leased = saveJob("leased", "https://example.com/leased.png", job ->
                job.leaseForSubmit(now.plusSeconds(60))
        );
        Job notLeased = saveJob("not-leased", "https://example.com/not-leased.png");

        int updated = jobRepository.markSubmitSucceeded(leased.getId(), "worker-1", now);
        int skipped = jobRepository.markSubmitSucceeded(notLeased.getId(), "worker-2", now);

        assertThat(updated).isEqualTo(1);
        assertThat(skipped).isZero();

        Job reloaded = jobRepository.findById(leased.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(JobStatus.PROCESSING);
        assertThat(reloaded.getLeaseType()).isEqualTo(JobLeaseType.NONE);
        assertThat(reloaded.getLeasedUntil()).isNull();
        assertThat(reloaded.getWorkerJobId()).isEqualTo("worker-1");
        assertThat(reloaded.getSubmittedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("releaseExpiredLeases는 만료된 lease만 해제한다")
    void releaseExpiredLeases_releasesOnlyExpiredLeases() {
        Instant now = Instant.now();
        Job expired = saveJob("expired", "https://example.com/expired.png", job ->
                job.leaseForSubmit(now.minusSeconds(1))
        );
        Job active = saveJob("active", "https://example.com/active.png", job ->
                job.leaseForSubmit(now.plusSeconds(60))
        );

        int released = jobRepository.releaseExpiredLeases(now);

        assertThat(released).isEqualTo(1);
        Job releasedJob = jobRepository.findById(expired.getId()).orElseThrow();
        assertThat(releasedJob.getLeaseType()).isEqualTo(JobLeaseType.NONE);
        assertThat(releasedJob.getLeasedUntil()).isNull();

        Job activeJob = jobRepository.findById(active.getId()).orElseThrow();
        assertThat(activeJob.getLeaseType()).isEqualTo(JobLeaseType.SUBMIT);
        assertThat(activeJob.getLeasedUntil()).isEqualTo(now.plusSeconds(60));
    }

    @Test
    @DisplayName("DB check constraint는 status와 leaseType의 불가능한 조합을 거부한다")
    void checkConstraint_rejectsInvalidStatusLeaseTypeCombination() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO jobs (
                    id,
                    idempotency_key,
                    image_url,
                    status,
                    lease_type,
                    leased_until,
                    created_at,
                    updated_at
                )
                VALUES (
                    UUID_TO_BIN(UUID()),
                    ?,
                    ?,
                    'COMPLETED',
                    'POLL',
                    NOW(6),
                    NOW(6),
                    NOW(6)
                )
                """, "invalid", "https://example.com/invalid.png"))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("DB check constraint는 PROCESSING Job의 workerJobId 누락을 거부한다")
    void checkConstraint_rejectsProcessingJobWithoutWorkerJobId() {
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO jobs (
                    id,
                    idempotency_key,
                    image_url,
                    status,
                    lease_type,
                    created_at,
                    updated_at
                )
                VALUES (
                    UUID_TO_BIN(UUID()),
                    ?,
                    ?,
                    'PROCESSING',
                    'NONE',
                    NOW(6),
                    NOW(6)
                )
                """, "missing-worker-id", "https://example.com/missing-worker-id.png"))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("서버 재기동 후 PENDING과 PROCESSING Job은 DB 상태만으로 다시 처리 대상이 된다")
    void restart_keepsJobsClaimableFromPersistedDatabaseState() {
        Instant now = Instant.now();
        Job pending = saveJob("restart-pending", "https://example.com/restart-pending.png");
        Job processing = saveProcessingJob("restart-processing", "worker-restart");
        flushAndClearAsRestart();

        List<Job> pendingJobs = jobRepository.findSubmitCandidatesForUpdateSkipLocked(10);
        List<Job> processingJobs = jobRepository.findPollCandidatesForUpdateSkipLocked(10);

        assertThat(pendingJobs)
                .extracting(Job::getId)
                .containsExactly(pending.getId());
        assertThat(processingJobs)
                .extracting(Job::getId)
                .containsExactly(processing.getId());
    }

    @Test
    @DisplayName("서버 재기동 후 만료된 lease는 recovery가 해제하고 다시 처리 대상이 된다")
    void restart_recoveryReleasesExpiredLeasesAndMakesJobsClaimableAgain() {
        Instant now = Instant.now();
        Job pendingLease = saveJob("restart-submit-lease", "https://example.com/restart-submit-lease.png", job ->
                job.leaseForSubmit(now.minusSeconds(1))
        );
        Job processingLease = saveJob("restart-poll-lease", "https://example.com/restart-poll-lease.png", job -> {
            ReflectionTestUtils.setField(job, "status", JobStatus.PROCESSING);
            ReflectionTestUtils.setField(job, "workerJobId", "worker-restart-lease");
            job.leaseForPoll(now.minusSeconds(1));
        });
        flushAndClearAsRestart();

        int released = jobRepository.releaseExpiredLeases(now);
        flushAndClearAsRestart();

        assertThat(released).isEqualTo(2);
        assertThat(jobRepository.findSubmitCandidatesForUpdateSkipLocked(10))
                .extracting(Job::getId)
                .containsExactly(pendingLease.getId());
        assertThat(jobRepository.findPollCandidatesForUpdateSkipLocked(10))
                .extracting(Job::getId)
                .containsExactly(processingLease.getId());
    }

    @Test
    @DisplayName("Mock Worker 제출 중 서버가 종료되면 lease 만료 전까지는 재전송하지 않고 만료 후 다시 제출 대상이 된다")
    void restart_doesNotResubmitInFlightSubmitLeaseUntilLeaseExpires() {
        Instant now = Instant.now();
        Job inFlightSubmit = saveJob("in-flight-submit", "https://example.com/in-flight-submit.png", job ->
                job.leaseForSubmit(now.plusSeconds(60))
        );
        flushAndClearAsRestart();

        assertThat(jobRepository.findSubmitCandidatesForUpdateSkipLocked(10))
                .extracting(Job::getId)
                .doesNotContain(inFlightSubmit.getId());

        int releasedBeforeTimeout = jobRepository.releaseExpiredLeases(now);
        assertThat(releasedBeforeTimeout).isZero();
        assertThat(jobRepository.findSubmitCandidatesForUpdateSkipLocked(10))
                .extracting(Job::getId)
                .doesNotContain(inFlightSubmit.getId());

        Instant afterLeaseTimeout = now.plusSeconds(61);
        int releasedAfterTimeout = jobRepository.releaseExpiredLeases(afterLeaseTimeout);
        flushAndClearAsRestart();

        assertThat(releasedAfterTimeout).isEqualTo(1);
        assertThat(jobRepository.findSubmitCandidatesForUpdateSkipLocked(10))
                .extracting(Job::getId)
                .containsExactly(inFlightSubmit.getId());
    }

    private Job saveProcessingJob(String key, String workerJobId) {
        return saveJob(key, "https://example.com/" + key + ".png", job -> {
            ReflectionTestUtils.setField(job, "status", JobStatus.PROCESSING);
            ReflectionTestUtils.setField(job, "workerJobId", workerJobId);
        });
    }

    private Job saveJob(String key, String imageUrl) {
        return saveJob(key, imageUrl, job -> {
        });
    }

    private Job saveJob(String key, String imageUrl, JobCustomizer customizer) {
        Job job = new Job(key, imageUrl);
        customizer.customize(job);
        return jobRepository.saveAndFlush(job);
    }

    private void flushAndClearAsRestart() {
        entityManager.flush();
        entityManager.clear();
    }

    @FunctionalInterface
    private interface JobCustomizer {
        void customize(Job job);
    }
}
