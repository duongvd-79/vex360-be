-- Flyway is disabled. Run this script manually after V22.
CREATE TABLE hall_items (
    id BINARY(16) NOT NULL,
    hall_id BINARY(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    media_asset_id BINARY(16) NOT NULL,
    display_order INT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_hall_items_hall_order (hall_id, display_order),
    INDEX idx_hall_items_media (media_asset_id),
    CONSTRAINT fk_hall_items_hall
        FOREIGN KEY (hall_id) REFERENCES exhibition_halls(id) ON DELETE CASCADE,
    CONSTRAINT fk_hall_items_media
        FOREIGN KEY (media_asset_id) REFERENCES media_assets(id)
);

ALTER TABLE hall_hotspots
    ADD COLUMN item_id BINARY(16),
    ADD COLUMN booth_slot_index INT,
    ADD INDEX idx_hall_hotspots_item (item_id),
    ADD INDEX idx_hall_hotspots_booth_slot (booth_slot_index),
    ADD CONSTRAINT fk_hall_hotspots_item
        FOREIGN KEY (item_id) REFERENCES hall_items(id);
