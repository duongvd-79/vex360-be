-- Run once against MySQL before deploying the immutable design-request release.
-- Flyway remains disabled; back up the database before executing this script.

ALTER TABLE design_requests
    ADD COLUMN contact_email VARCHAR(320) NULL AFTER note,
    ADD COLUMN contact_phone VARCHAR(20) NULL AFTER contact_email;

UPDATE design_requests dr
JOIN companies company ON company.id = dr.company_id
SET dr.contact_email = company.email,
    dr.contact_phone = company.phone
WHERE dr.contact_email IS NULL
   OR dr.contact_phone IS NULL;

CREATE TABLE design_request_media_assets (
    id BINARY(16) NOT NULL,
    design_request_id BINARY(16) NOT NULL,
    media_asset_id BINARY(16) NOT NULL,
    required_from_baseline BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_design_request_media_asset UNIQUE (design_request_id, media_asset_id),
    CONSTRAINT fk_design_request_media_asset_request
        FOREIGN KEY (design_request_id) REFERENCES design_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_design_request_media_asset_asset
        FOREIGN KEY (media_asset_id) REFERENCES media_assets(id) ON DELETE CASCADE
);

-- Preserve the previous visibility behavior for every non-terminal request.
-- Media already referenced by a redesign baseline is marked as required.
INSERT INTO design_request_media_assets (
    id, design_request_id, media_asset_id, required_from_baseline, created_at)
SELECT UUID_TO_BIN(UUID()),
       dr.id,
       media_asset.id,
       CASE
           WHEN dr.mode = 'REDESIGN' AND EXISTS (
               SELECT 1
               FROM hotspots hotspot
               JOIN panoramas panorama ON panorama.id = hotspot.source_panorama_id
               WHERE panorama.booth_id = dr.booth_id
                 AND hotspot.media_asset_id = media_asset.id
           ) THEN TRUE
           ELSE FALSE
       END,
       CURRENT_TIMESTAMP(6)
FROM design_requests dr
JOIN media_assets media_asset ON media_asset.company_id = dr.company_id
WHERE dr.status IN ('PENDING', 'ASSIGNED', 'DRAFT_SUBMITTED', 'REVISION_REQUESTED');

CREATE INDEX idx_design_request_media_asset_asset
    ON design_request_media_assets (media_asset_id, design_request_id);
