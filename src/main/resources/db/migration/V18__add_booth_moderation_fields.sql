ALTER TABLE booths
    ADD COLUMN warning_count INT DEFAULT 0,
    ADD COLUMN warning_reason TEXT,
    ADD COLUMN warned_at DATETIME(6),
    ADD COLUMN warned_by_id BINARY(16),
    ADD COLUMN ban_reason TEXT,
    ADD COLUMN banned_at DATETIME(6),
    ADD COLUMN banned_by_id BINARY(16),
    ADD CONSTRAINT fk_booths_warned_by FOREIGN KEY (warned_by_id) REFERENCES users(id),
    ADD CONSTRAINT fk_booths_banned_by FOREIGN KEY (banned_by_id) REFERENCES users(id);
