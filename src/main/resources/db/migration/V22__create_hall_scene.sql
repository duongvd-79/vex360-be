-- Flyway is disabled. Run this script manually after V21.
CREATE TABLE hall_panoramas (
    id BINARY(16) NOT NULL,
    hall_id BINARY(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    image_url VARCHAR(1000) NOT NULL,
    image_key VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    order_index INT NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    INDEX idx_hall_panoramas_hall_order (hall_id, order_index),
    CONSTRAINT fk_hall_panoramas_hall
        FOREIGN KEY (hall_id) REFERENCES exhibition_halls(id) ON DELETE CASCADE
);

CREATE TABLE hall_hotspots (
    id BINARY(16) NOT NULL,
    source_panorama_id BINARY(16) NOT NULL,
    type VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    x_position DOUBLE NOT NULL,
    y_position DOUBLE NOT NULL,
    z_position DOUBLE NOT NULL,
    icon_style VARCHAR(100),
    scale DOUBLE,
    z_index INT,
    target_panorama_id BINARY(16),
    media_asset_id BINARY(16),
    info_text TEXT,
    media_click_action VARCHAR(50),
    corner_tl_x DOUBLE,
    corner_tl_y DOUBLE,
    corner_tl_z DOUBLE,
    corner_tr_x DOUBLE,
    corner_tr_y DOUBLE,
    corner_tr_z DOUBLE,
    corner_bl_x DOUBLE,
    corner_bl_y DOUBLE,
    corner_bl_z DOUBLE,
    corner_br_x DOUBLE,
    corner_br_y DOUBLE,
    corner_br_z DOUBLE,
    PRIMARY KEY (id),
    INDEX idx_hall_hotspots_source (source_panorama_id),
    INDEX idx_hall_hotspots_target (target_panorama_id),
    INDEX idx_hall_hotspots_media (media_asset_id),
    CONSTRAINT fk_hall_hotspots_source
        FOREIGN KEY (source_panorama_id) REFERENCES hall_panoramas(id) ON DELETE CASCADE,
    CONSTRAINT fk_hall_hotspots_target
        FOREIGN KEY (target_panorama_id) REFERENCES hall_panoramas(id),
    CONSTRAINT fk_hall_hotspots_media
        FOREIGN KEY (media_asset_id) REFERENCES media_assets(id)
);
