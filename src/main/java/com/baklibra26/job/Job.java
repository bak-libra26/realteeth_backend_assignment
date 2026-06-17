package com.baklibra26.job;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;


@Getter
@Entity
@Table(
        name = "jobs",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_jobs_idempotency_key",
                        columnNames = "idempotency_key"
                )
        },
        indexes = {
                @Index(name = "idx_jobs_status_lease_type_created_at", columnList = "status, lease_type, created_at"),
                @Index(name = "idx_jobs_lease_type_leased_until", columnList = "lease_type, leased_until"),
                @Index(name = "idx_jobs_worker_job_id", columnList = "worker_job_id")
        }
)
@Check(constraints = """
    (
        (lease_type = 'NONE' AND leased_until IS NULL)
        OR
        (lease_type <> 'NONE' AND leased_until IS NOT NULL)
    )
	    AND
	    (
	        (status = 'PENDING' AND lease_type IN ('NONE', 'SUBMIT'))
	        OR
	        (status = 'PROCESSING' AND lease_type IN ('NONE', 'POLL'))
	        OR
	        (status IN ('COMPLETED', 'FAILED') AND lease_type = 'NONE')
	    )
	    AND
	    (
	        (status = 'PENDING' AND worker_job_id IS NULL)
	        OR
	        (status IN ('PROCESSING', 'COMPLETED') AND worker_job_id IS NOT NULL)
	        OR
	        (status = 'FAILED')
	    )
	""")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Job {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;                                    // 서버에서 사용하는 식별키

    @Column(name = "worker_job_id", length = 128)
    private String workerJobId;                         // mock worker 가 제공해주는 식별키

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 128)
    private String idempotencyKey;                      // 클라이언트의 요청을 식별할 때 사용할 멱등키

    @Column(nullable = false, length = 2048)
    private String imageUrl;                            // 클라이언트가 제공하는 이미지 URL

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private JobStatus status;                           // Job의 상태를 구분하기 위한 상태값

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "lease_type", nullable = false, length = 30)
    private JobLeaseType leaseType;

    @Column(name = "leased_until")
    private Instant leasedUntil;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(length = 4096)
    private String result;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;


    public Job(String idempotencyKey, String imageUrl) {
        this.id = UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.imageUrl = imageUrl;
        this.status = JobStatus.PENDING;
        this.leaseType = JobLeaseType.NONE;
    }

    public void leaseForSubmit(Instant leasedUntil) {
        this.leaseType = JobLeaseType.SUBMIT;
        this.leasedUntil = leasedUntil;
    }

    public void leaseForPoll(Instant leasedUntil) {
        this.leaseType = JobLeaseType.POLL;
        this.leasedUntil = leasedUntil;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
