-- Flyway is disabled. Run this script manually after V25.
ALTER TABLE hall_hotspots
    ADD COLUMN info_content_type VARCHAR(50) NULL AFTER info_text;

UPDATE hall_hotspots
SET info_content_type = CASE
    WHEN TRIM(COALESCE(info_text, '')) <> '' THEN 'TEXT'
    ELSE 'NONE'
END
WHERE type = 'INFO' AND info_content_type IS NULL;
