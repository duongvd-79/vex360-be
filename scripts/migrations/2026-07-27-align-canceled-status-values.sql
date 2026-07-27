-- Keep persisted enum values aligned with their Java enum constants.
UPDATE exhibitor_registrations
SET status = 'CANCELED'
WHERE status = 'CANCELLED';

UPDATE design_requests
SET status = 'CANCELED'
WHERE status = 'CANCELLED';

UPDATE storage_package_orders
SET status = 'CANCELLED'
WHERE status = 'CANCELED';
