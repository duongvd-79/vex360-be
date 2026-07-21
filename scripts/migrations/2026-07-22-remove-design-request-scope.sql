-- Run only on databases that already applied the earlier 2026-07-21 script
-- while design_requests.scope and idx_design_request_mode_scope_status existed.

DROP INDEX idx_design_request_mode_scope_status ON design_requests;

ALTER TABLE design_requests
    DROP COLUMN scope;

CREATE INDEX idx_design_request_mode_status
    ON design_requests (mode, status);
