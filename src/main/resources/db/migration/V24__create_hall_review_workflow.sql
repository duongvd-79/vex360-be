-- Flyway is disabled. Run this script manually after V23.
CREATE TABLE hall_review_requests (
    id BINARY(16) NOT NULL,
    hall_id BINARY(16) NOT NULL,
    status VARCHAR(50) NOT NULL,
    submitted_by_id BINARY(16) NOT NULL,
    submitted_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    reviewed_by_id BINARY(16),
    reviewed_at DATETIME(6),
    rejected_reason TEXT,
    content_snapshot_json LONGTEXT NOT NULL,
    change_summary_json LONGTEXT NOT NULL,
    version_number INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_hall_review_requests_version UNIQUE (hall_id, version_number),
    INDEX idx_hall_review_requests_status_submitted (status, submitted_at),
    CONSTRAINT fk_hall_review_requests_hall
        FOREIGN KEY (hall_id) REFERENCES exhibition_halls(id) ON DELETE CASCADE,
    CONSTRAINT fk_hall_review_requests_submitter
        FOREIGN KEY (submitted_by_id) REFERENCES users(id),
    CONSTRAINT fk_hall_review_requests_reviewer
        FOREIGN KEY (reviewed_by_id) REFERENCES users(id)
);

CREATE TABLE hall_published_revisions (
    id BINARY(16) NOT NULL,
    hall_id BINARY(16) NOT NULL,
    version_number INT NOT NULL,
    content_snapshot_json LONGTEXT NOT NULL,
    published_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_hall_published_revisions_hall UNIQUE (hall_id),
    CONSTRAINT fk_hall_published_revisions_hall
        FOREIGN KEY (hall_id) REFERENCES exhibition_halls(id) ON DELETE CASCADE
);
