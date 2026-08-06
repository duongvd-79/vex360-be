-- Run once against MySQL when deploying removal of package-template storage limits.
-- Flyway remains disabled; back up the database before executing this script.

ALTER TABLE exhibitor_registrations
    DROP COLUMN storage_limit_mb_snapshot;

ALTER TABLE package_templates
    DROP COLUMN storage_limit_mb;
