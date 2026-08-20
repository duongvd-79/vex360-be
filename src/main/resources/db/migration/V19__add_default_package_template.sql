-- Flyway is disabled. Run this script manually before deploying the backend.
ALTER TABLE package_templates
    ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN default_uniqueness_guard TINYINT
        GENERATED ALWAYS AS (
            CASE WHEN is_default = TRUE THEN 1 ELSE NULL END
        ) STORED,
    ADD UNIQUE INDEX uk_package_templates_single_default
        (default_uniqueness_guard);

-- After this script, set one ACTIVE package as default through the Admin API:
-- PATCH /api/v1/admin/package-templates/{id}/default
