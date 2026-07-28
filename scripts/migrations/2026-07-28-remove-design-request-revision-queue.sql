-- Run once on databases that previously applied the revision-queue schema.
-- No status data migration is required because the queue status is absent.

ALTER TABLE design_requests
    DROP INDEX idx_design_request_assignment_queue,
    DROP COLUMN revision_queued_at;
