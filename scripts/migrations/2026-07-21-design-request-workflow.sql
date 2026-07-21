-- Run once against MySQL before deploying the design-request workflow release.
-- Flyway remains disabled; execute this script in a transaction-capable maintenance window.

ALTER TABLE design_requests
    ADD COLUMN mode VARCHAR(32) NULL AFTER status,
    ADD COLUMN quota_charged BOOLEAN NOT NULL DEFAULT TRUE AFTER review_count,
    ADD COLUMN revision_queued_at DATETIME(6) NULL AFTER quota_charged,
    ADD COLUMN cancellation_status VARCHAR(16) NOT NULL DEFAULT 'NONE' AFTER revision_queued_at,
    ADD COLUMN cancellation_reason VARCHAR(2000) NULL AFTER cancellation_status,
    ADD COLUMN cancellation_requested_at DATETIME(6) NULL AFTER cancellation_reason,
    ADD COLUMN cancellation_resolved_at DATETIME(6) NULL AFTER cancellation_requested_at,
    ADD COLUMN cancellation_resolution_note VARCHAR(2000) NULL AFTER cancellation_resolved_at;

UPDATE design_requests
SET mode = 'INITIAL_DESIGN';

UPDATE design_requests
SET quota_charged = FALSE
WHERE status = 'CANCELED'
  AND assigned_designer_user_id IS NULL;

ALTER TABLE design_requests
    MODIFY COLUMN mode VARCHAR(32) NOT NULL;

CREATE TABLE design_request_products (
    id BINARY(16) NOT NULL,
    design_request_id BINARY(16) NOT NULL,
    product_id BINARY(16) NOT NULL,
    required_from_baseline BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_design_request_product UNIQUE (design_request_id, product_id),
    CONSTRAINT fk_design_request_product_request
        FOREIGN KEY (design_request_id) REFERENCES design_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_design_request_product_product
        FOREIGN KEY (product_id) REFERENCES products(id)
);

-- Legacy requests keep their previous visibility behavior after deployment.
INSERT INTO design_request_products (
    id, design_request_id, product_id, required_from_baseline, created_at)
SELECT UUID_TO_BIN(UUID()), dr.id, product.id, FALSE, CURRENT_TIMESTAMP(6)
FROM design_requests dr
JOIN products product ON product.company_id = dr.company_id
WHERE product.status = 'ACTIVE';

CREATE TABLE design_request_messages (
    id BINARY(16) NOT NULL,
    design_request_id BINARY(16) NOT NULL,
    sender_user_id BINARY(16) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_design_request_message_request
        FOREIGN KEY (design_request_id) REFERENCES design_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_design_request_message_sender
        FOREIGN KEY (sender_user_id) REFERENCES users(id)
);

ALTER TABLE design_drafts
    ADD COLUMN booth_name VARCHAR(255) NULL AFTER note,
    ADD COLUMN booth_description TEXT NULL AFTER booth_name,
    ADD COLUMN display_template_key VARCHAR(100) NULL AFTER booth_description,
    ADD COLUMN thumbnail_action VARCHAR(16) NOT NULL DEFAULT 'KEEP' AFTER display_template_key,
    ADD COLUMN thumbnail_asset_id BINARY(16) NULL AFTER thumbnail_action,
    ADD COLUMN background_music_action VARCHAR(16) NOT NULL DEFAULT 'KEEP' AFTER thumbnail_asset_id,
    ADD COLUMN background_music_asset_id BINARY(16) NULL AFTER background_music_action;

ALTER TABLE design_draft_assets
    ADD COLUMN asset_type VARCHAR(32) NOT NULL DEFAULT 'PANORAMA' AFTER file_size,
    ADD COLUMN asset_source VARCHAR(32) NOT NULL DEFAULT 'UPLOADED' AFTER asset_type;

ALTER TABLE design_drafts
    ADD CONSTRAINT fk_design_draft_thumbnail_asset
        FOREIGN KEY (thumbnail_asset_id) REFERENCES design_draft_assets(id),
    ADD CONSTRAINT fk_design_draft_background_music_asset
        FOREIGN KEY (background_music_asset_id) REFERENCES design_draft_assets(id);

CREATE INDEX idx_design_request_booth_status
    ON design_requests (booth_id, status);
CREATE INDEX idx_design_request_assignment_queue
    ON design_requests (assigned_designer_user_id, status, revision_queued_at);
CREATE INDEX idx_design_request_mode_status
    ON design_requests (mode, status);
CREATE INDEX idx_design_request_product_product
    ON design_request_products (product_id, design_request_id);
CREATE INDEX idx_design_request_message_created
    ON design_request_messages (design_request_id, created_at);
CREATE INDEX idx_design_draft_asset_type_source
    ON design_draft_assets (design_request_id, asset_type, asset_source);
