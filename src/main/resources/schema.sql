CREATE TABLE IF NOT EXISTS jobs (
    id BINARY(16) NOT NULL,
    worker_job_id VARCHAR(128),
    idempotency_key VARCHAR(128) NOT NULL,
    image_url VARCHAR(2048) NOT NULL,
    status VARCHAR(30) NOT NULL,
    lease_type VARCHAR(30) NOT NULL,
    leased_until DATETIME(6),
    submitted_at DATETIME(6),
    finished_at DATETIME(6),
    result VARCHAR(4096),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_jobs_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT chk_jobs_lease_fields CHECK (
        (lease_type = 'NONE' AND leased_until IS NULL)
        OR
        (lease_type <> 'NONE' AND leased_until IS NOT NULL)
    ),
    CONSTRAINT chk_jobs_status_lease_type CHECK (
        (status = 'PENDING' AND lease_type IN ('NONE', 'SUBMIT'))
        OR
        (status = 'PROCESSING' AND lease_type IN ('NONE', 'POLL'))
        OR
        (status IN ('COMPLETED', 'FAILED') AND lease_type = 'NONE')
    ),
    CONSTRAINT chk_jobs_worker_job_id_by_status CHECK (
        (status = 'PENDING' AND worker_job_id IS NULL)
        OR
        (status IN ('PROCESSING', 'COMPLETED') AND worker_job_id IS NOT NULL)
        OR
        (status = 'FAILED')
    ),
    INDEX idx_jobs_status_lease_type_created_at (status, lease_type, created_at),
    INDEX idx_jobs_lease_type_leased_until (lease_type, leased_until),
    INDEX idx_jobs_worker_job_id (worker_job_id)
);
