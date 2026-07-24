-- Run once against MySQL before deploying deferred quota for Designer media.
-- Flyway remains disabled. The Designer media feature is assumed not to have
-- production data yet; the guard below aborts if that assumption is false.

DELIMITER //
CREATE PROCEDURE assert_no_existing_designer_media()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM design_draft_assets
        WHERE asset_type = 'MEDIA_ATTACHMENT'
        LIMIT 1
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Existing MEDIA_ATTACHMENT rows require quota reconciliation before migration';
    END IF;
END//
DELIMITER ;

CALL assert_no_existing_designer_media();
DROP PROCEDURE assert_no_existing_designer_media;

ALTER TABLE companies
    ADD COLUMN storage_reserved_bytes BIGINT NOT NULL DEFAULT 0
        AFTER storage_used_bytes;

ALTER TABLE design_draft_assets
    ADD COLUMN quota_state VARCHAR(50) NOT NULL DEFAULT 'CHARGED'
        AFTER asset_source;

CREATE TABLE design_draft_media_assets (
    id BINARY(16) NOT NULL,
    draft_id BINARY(16) NOT NULL,
    asset_id BINARY(16) NOT NULL,
    title VARCHAR(255) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_design_draft_media_asset UNIQUE (draft_id, asset_id),
    CONSTRAINT fk_design_draft_media_asset_draft
        FOREIGN KEY (draft_id) REFERENCES design_drafts(id) ON DELETE CASCADE,
    CONSTRAINT fk_design_draft_media_asset_asset
        FOREIGN KEY (asset_id) REFERENCES design_draft_assets(id)
);

UPDATE design_draft_assets
SET quota_state = 'NONE'
WHERE asset_source = 'BOOTH_BASELINE';

CREATE INDEX idx_design_draft_assets_quota_state
    ON design_draft_assets (quota_state);

CREATE INDEX idx_design_draft_media_assets_order
    ON design_draft_media_assets (draft_id, sort_order);
