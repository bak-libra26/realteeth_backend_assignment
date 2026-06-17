package com.baklibra26.job;

import com.baklibra26.job.exceptions.IdempotencyConflictException;
import com.baklibra26.job.exceptions.JobNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobService")
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @InjectMocks
    private JobService jobService;

    @Test
    @DisplayName("새 멱등키로 작업을 생성하면 PENDING 작업을 저장한다")
    void createJob_savesNewJob_whenIdempotencyKeyIsNew() {
        // given
        Job saved = new Job("key-1", "https://example.com/image.png");
        when(jobRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(saved));

        // when
        Job job = jobService.createJob("key-1", "https://example.com/image.png");

        // then
        assertThat(job.getIdempotencyKey()).isEqualTo("key-1");
        assertThat(job.getImageUrl()).isEqualTo("https://example.com/image.png");
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        verify(jobRepository).saveIfAbsent(any(), eq("key-1"), eq("https://example.com/image.png"), any());
    }

    @Test
    @DisplayName("같은 멱등키와 같은 이미지 URL이면 기존 작업을 반환한다")
    void createJob_returnsExistingJob_whenSameIdempotencyKeyAndSameImageUrl() {
        // given
        Job existing = new Job("key-1", "https://example.com/image.png");
        when(jobRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        // when
        Job job = jobService.createJob("key-1", "https://example.com/image.png");

        // then
        assertThat(job).isSameAs(existing);
    }

    @Test
    @DisplayName("같은 멱등키로 다른 이미지 URL을 요청하면 충돌 예외가 발생한다")
    void createJob_throwsConflict_whenSameIdempotencyKeyAndDifferentImageUrl() {
        // given
        Job existing = new Job("key-1", "https://example.com/image-a.png");
        when(jobRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        // when & then
        assertThatThrownBy(() -> jobService.createJob("key-1", "https://example.com/image-b.png"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    @DisplayName("동시 생성으로 멱등키 unique 충돌이 발생하면 기존 작업을 반환한다")
    void createJob_returnsExistingJob_whenConcurrentCreateConflictsWithSameImageUrl() {
        // given
        Job existing = new Job("key-1", "https://example.com/image.png");
        when(jobRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        // when
        Job job = jobService.createJob("key-1", "https://example.com/image.png");

        // then
        assertThat(job).isSameAs(existing);
    }

    @Test
    @DisplayName("동시 생성 unique 충돌 후 기존 작업의 이미지 URL이 다르면 충돌 예외가 발생한다")
    void createJob_throwsConflict_whenConcurrentCreateConflictsWithDifferentImageUrl() {
        // given
        Job existing = new Job("key-1", "https://example.com/image-a.png");
        when(jobRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        // when & then
        assertThatThrownBy(() -> jobService.createJob("key-1", "https://example.com/image-b.png"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    @DisplayName("작업 ID로 작업을 조회한다")
    void getJobById_returnsJob_whenFound() {
        // given
        UUID jobId = UUID.randomUUID();
        Job existing = new Job("key-1", "https://example.com/image.png");
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(existing));

        // when
        Job job = jobService.getJobById(jobId);

        // then
        assertThat(job).isSameAs(existing);
    }

    @Test
    @DisplayName("작업 ID가 없으면 조회 예외가 발생한다")
    void getJobById_throwsJobNotFoundException_whenNotFound() {
        // given
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> jobService.getJobById(jobId))
                .isInstanceOf(JobNotFoundException.class)
                .hasMessageContaining("Job not found");
    }

    @Test
    @DisplayName("제출 대상 작업을 선점하면 submit lease를 설정한다")
    void claimJobsForSubmit_leasesJobsForSubmit() {
        // given
        Job job = new Job("key-1", "https://example.com/image.png");
        when(jobRepository.findSubmitCandidatesForUpdateSkipLocked(eq(10))).thenReturn(List.of(job));

        // when
        List<Job> jobs = jobService.claimJobsForSubmit(10, Duration.ofMinutes(1));

        // then
        assertThat(jobs).containsExactly(job);
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.getLeaseType()).isEqualTo(JobLeaseType.SUBMIT);
        assertThat(job.getLeasedUntil()).isNotNull();
    }

    @Test
    @DisplayName("poll 대상 작업을 선점하면 poll lease를 설정한다")
    void claimJobsForPoll_leasesJobsForPoll() {
        // given
        Job job = new Job("key-1", "https://example.com/image.png");
        ReflectionTestUtils.setField(job, "status", JobStatus.PROCESSING);
        when(jobRepository.findPollCandidatesForUpdateSkipLocked(eq(10))).thenReturn(List.of(job));

        // when
        List<Job> jobs = jobService.claimJobsForPoll(10, Duration.ofMinutes(1));

        // then
        assertThat(jobs).containsExactly(job);
        assertThat(job.getStatus()).isEqualTo(JobStatus.PROCESSING);
        assertThat(job.getLeaseType()).isEqualTo(JobLeaseType.POLL);
        assertThat(job.getLeasedUntil()).isNotNull();
    }

    @Test
    @DisplayName("lease 복구 시 현재 시각 기준으로 만료 lease를 해제한다")
    void recoverExpiredLeases_releasesExpiredLeases() {
        // given
        when(jobRepository.releaseExpiredLeases(any())).thenReturn(2);

        // when
        int released = jobService.recoverExpiredLeases();

        // then
        verify(jobRepository).releaseExpiredLeases(any());
        assertThat(released).isEqualTo(2);
    }
}
