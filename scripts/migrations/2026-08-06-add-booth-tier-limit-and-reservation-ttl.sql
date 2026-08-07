-- Run once against MySQL before deploying booth tier limits & slot reservation release.
-- Flyway remains disabled; back up the database before executing this script.

ALTER TABLE exhibition_packages
    ADD COLUMN max_booths INT NULL AFTER final_price;

ALTER TABLE exhibitor_registrations
    ADD COLUMN reserved_until DATETIME NULL AFTER status,
    ADD INDEX idx_exhibitor_registrations_reservation (status, reserved_until);
