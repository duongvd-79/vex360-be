ALTER TABLE exhibition_packages
    ADD COLUMN package_name_snapshot VARCHAR(255) NULL,
    ADD COLUMN package_description_snapshot TEXT NULL,
    ADD COLUMN price_snapshot DECIMAL(15, 2) NULL,
    ADD COLUMN currency_snapshot VARCHAR(10) NULL,
    ADD COLUMN max_products_per_booth_snapshot INT NULL,
    ADD COLUMN max_embedded_videos_per_booth_snapshot INT NULL,
    ADD COLUMN max_panoramas_per_booth_snapshot INT NULL,
    ADD COLUMN max_hotspots_per_booth_snapshot INT NULL,
    ADD COLUMN listing_priority_snapshot VARCHAR(50) NULL;

UPDATE exhibition_packages ep
JOIN package_templates pt ON pt.id = ep.template_id
SET ep.package_name_snapshot = pt.name,
    ep.package_description_snapshot = pt.description,
    ep.price_snapshot = pt.price,
    ep.currency_snapshot = pt.currency,
    ep.max_products_per_booth_snapshot = pt.max_products_per_booth,
    ep.max_embedded_videos_per_booth_snapshot = pt.max_embedded_videos_per_booth,
    ep.max_panoramas_per_booth_snapshot = pt.max_panoramas_per_booth,
    ep.max_hotspots_per_booth_snapshot = pt.max_hotspots_per_booth,
    ep.listing_priority_snapshot = pt.listing_priority;

ALTER TABLE exhibition_packages
    MODIFY package_name_snapshot VARCHAR(255) NOT NULL,
    MODIFY package_description_snapshot TEXT NOT NULL,
    MODIFY price_snapshot DECIMAL(15, 2) NOT NULL,
    MODIFY currency_snapshot VARCHAR(10) NOT NULL,
    MODIFY max_products_per_booth_snapshot INT NOT NULL,
    MODIFY max_embedded_videos_per_booth_snapshot INT NOT NULL,
    MODIFY max_panoramas_per_booth_snapshot INT NOT NULL,
    MODIFY max_hotspots_per_booth_snapshot INT NOT NULL,
    MODIFY listing_priority_snapshot VARCHAR(50) NOT NULL;
