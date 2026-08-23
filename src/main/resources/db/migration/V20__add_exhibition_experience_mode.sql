-- Flyway is disabled. Run this script manually before deploying the backend.
ALTER TABLE exhibitions
    ADD COLUMN experience_mode VARCHAR(50) NULL DEFAULT 'WITH_BOOTHS';

UPDATE exhibitions
SET experience_mode = 'WITH_BOOTHS'
WHERE experience_mode IS NULL;

ALTER TABLE exhibitions
    MODIFY COLUMN experience_mode VARCHAR(50) NOT NULL DEFAULT 'WITH_BOOTHS';
