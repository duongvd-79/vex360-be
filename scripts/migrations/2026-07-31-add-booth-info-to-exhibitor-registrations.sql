-- Run once against MySQL before deploying the exhibitor registration booth-info release.
-- Flyway remains disabled; back up the database before executing this script.

ALTER TABLE exhibitor_registrations
    ADD COLUMN booth_name VARCHAR(255) NULL AFTER participation_reason,
    ADD COLUMN booth_description TEXT NULL AFTER booth_name;

UPDATE exhibitor_registrations registration
JOIN companies company ON company.id = registration.company_id
SET registration.booth_name = LEFT(TRIM(company.name), 255),
    registration.booth_description = LEFT(
        COALESCE(
            NULLIF(TRIM(company.description), ''),
            CONCAT('Gian hàng của ', TRIM(company.name))
        ),
        2000
    )
WHERE registration.booth_name IS NULL
   OR registration.booth_description IS NULL;

ALTER TABLE exhibitor_registrations
    MODIFY COLUMN booth_name VARCHAR(255) NOT NULL,
    MODIFY COLUMN booth_description TEXT NOT NULL;
