-- Flyway is disabled. Run this script manually before deploying the backend.
ALTER TABLE exhibition_halls
    ADD COLUMN background_music_url VARCHAR(1000) NULL,
    ADD COLUMN background_music_public_id VARCHAR(500) NULL,
    ADD COLUMN background_music_file_name VARCHAR(255) NULL,
    ADD COLUMN background_music_file_size BIGINT NULL;
