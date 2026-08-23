-- Flyway is disabled. Run this script manually before deploying the backend.
CREATE TABLE exhibition_halls (
    id BINARY(16) NOT NULL,
    exhibition_id INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_exhibition_halls_exhibition UNIQUE (exhibition_id),
    CONSTRAINT fk_exhibition_halls_exhibition
        FOREIGN KEY (exhibition_id) REFERENCES exhibitions(id)
);
