-- Migration script: Convert all Enum columns across all tables to VARCHAR(50)
-- Date: 2026-07-22

-- 1. Users
ALTER TABLE users MODIFY COLUMN role VARCHAR(50) NOT NULL;
ALTER TABLE users MODIFY COLUMN provider VARCHAR(50) NULL;
ALTER TABLE users MODIFY COLUMN user_status VARCHAR(50) NOT NULL;

-- 2. Partnership Requests
ALTER TABLE partnership_requests MODIFY COLUMN requested_role VARCHAR(50) NOT NULL;
ALTER TABLE partnership_requests MODIFY COLUMN account_action VARCHAR(50) NOT NULL;
ALTER TABLE partnership_requests MODIFY COLUMN status VARCHAR(50) NOT NULL;

-- 3. Package Templates
ALTER TABLE package_templates MODIFY COLUMN listing_priority VARCHAR(50) NOT NULL;
ALTER TABLE package_templates MODIFY COLUMN status VARCHAR(50) NOT NULL;

-- 4. Product Contents
ALTER TABLE product_contents MODIFY COLUMN type VARCHAR(50) NOT NULL;

-- 5. Product Categories
ALTER TABLE product_categories MODIFY COLUMN status VARCHAR(50) NOT NULL;

-- 6. Products
ALTER TABLE products MODIFY COLUMN status VARCHAR(50) NOT NULL;

-- 7. Notifications
ALTER TABLE notifications MODIFY COLUMN recipient_role VARCHAR(50) NULL;

-- 8. Exhibition Assets
ALTER TABLE exhibition_assets MODIFY COLUMN asset_type VARCHAR(50) NOT NULL;

-- 9. Exhibition Packages
ALTER TABLE exhibition_packages MODIFY COLUMN status VARCHAR(50) NOT NULL;

-- 10. Payments
ALTER TABLE payments MODIFY COLUMN payment_type VARCHAR(50) NOT NULL;
ALTER TABLE payments MODIFY COLUMN status VARCHAR(50) NOT NULL;

-- 11. Exhibitor Registrations
ALTER TABLE exhibitor_registrations MODIFY COLUMN status VARCHAR(50) NOT NULL;
ALTER TABLE exhibitor_registrations MODIFY COLUMN listing_priority_snapshot VARCHAR(50) NULL;

-- 12. Exhibitions
ALTER TABLE exhibitions MODIFY COLUMN status VARCHAR(50) NOT NULL;

-- 13. Design Drafts
ALTER TABLE design_drafts MODIFY COLUMN thumbnail_action VARCHAR(50) NOT NULL;
ALTER TABLE design_drafts MODIFY COLUMN background_music_action VARCHAR(50) NOT NULL;

-- 14. Design Draft Hotspots
ALTER TABLE design_draft_hotspots MODIFY COLUMN type VARCHAR(50) NOT NULL;
ALTER TABLE design_draft_hotspots MODIFY COLUMN media_click_action VARCHAR(50) NULL;
ALTER TABLE design_draft_hotspots MODIFY COLUMN info_content_type VARCHAR(50) NULL;

-- 15. Design Requests
ALTER TABLE design_requests MODIFY COLUMN status VARCHAR(50) NOT NULL;
ALTER TABLE design_requests MODIFY COLUMN mode VARCHAR(50) NOT NULL;
ALTER TABLE design_requests MODIFY COLUMN cancellation_status VARCHAR(50) NOT NULL;

-- 16. Design Draft Assets
ALTER TABLE design_draft_assets MODIFY COLUMN asset_type VARCHAR(50) NOT NULL;
ALTER TABLE design_draft_assets MODIFY COLUMN asset_source VARCHAR(50) NOT NULL;

-- 17. Companies
ALTER TABLE companies MODIFY COLUMN status VARCHAR(50) NOT NULL;

-- 18. Storage Package Orders
ALTER TABLE storage_package_orders MODIFY COLUMN status VARCHAR(50) NOT NULL;

-- 19. Analytics Events
ALTER TABLE analytics_events MODIFY COLUMN event_type VARCHAR(50) NOT NULL;

-- 20. Booths
ALTER TABLE booths MODIFY COLUMN status VARCHAR(50) NOT NULL;

-- 21. Media Assets
ALTER TABLE media_assets MODIFY COLUMN type VARCHAR(50) NOT NULL;

-- 22. Hotspots
ALTER TABLE hotspots MODIFY COLUMN type VARCHAR(50) NOT NULL;
ALTER TABLE hotspots MODIFY COLUMN media_click_action VARCHAR(50) NULL;
ALTER TABLE hotspots MODIFY COLUMN info_content_type VARCHAR(50) NULL;

-- 23. Booth Review Requests
ALTER TABLE booth_review_requests MODIFY COLUMN status VARCHAR(50) NOT NULL;
