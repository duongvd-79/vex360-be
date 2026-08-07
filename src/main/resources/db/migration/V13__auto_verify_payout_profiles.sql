-- Migration: Auto-verify existing payout profiles in database
-- File: V13__auto_verify_payout_profiles.sql

UPDATE company_payout_profiles 
SET status = 'VERIFIED',
    verified_at = COALESCE(verified_at, NOW()),
    rejected_by = NULL,
    rejected_at = NULL,
    rejected_reason = NULL
WHERE status IN ('PENDING_VERIFICATION', 'REJECTED');
