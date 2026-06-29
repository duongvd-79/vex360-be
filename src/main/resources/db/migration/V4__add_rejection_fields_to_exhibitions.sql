ALTER TABLE exhibitions ADD COLUMN rejected_reason TEXT NULL;
ALTER TABLE exhibitions ADD COLUMN reviewed_by_user_id BINARY(16) NULL;
ALTER TABLE exhibitions ADD COLUMN reviewed_at TIMESTAMP NULL;

ALTER TABLE exhibitions ADD CONSTRAINT fk_exhibition_reviewer FOREIGN KEY (reviewed_by_user_id) REFERENCES users(id) ON DELETE SET NULL;
