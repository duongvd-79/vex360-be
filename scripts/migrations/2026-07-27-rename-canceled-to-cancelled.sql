-- Keep persisted enum values aligned with ExhibitorRegistrationStatus and DesignRequestStatus.
UPDATE exhibitor_registrations
SET status = 'CANCELLED'
WHERE status = 'CANCELED';

UPDATE design_requests
SET status = 'CANCELLED'
WHERE status = 'CANCELED';
