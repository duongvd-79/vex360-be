-- Run once against MySQL before deploying storage package payments.
-- Flyway remains disabled; back up the database before executing this script.

ALTER TABLE payments
    MODIFY COLUMN exhibitor_registration_id INT NULL;
