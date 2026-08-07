-- Migration: Simplify payout profile and withdrawal request account numbers (remove encryption columns, add plain accountNumber)
-- File: V15__simplify_payout_profile_account_number.sql

ALTER TABLE company_payout_profiles
    ADD COLUMN account_number VARCHAR(50) NULL AFTER bank_name_snapshot;

-- Populate account_number with fallback value if last4 exists
UPDATE company_payout_profiles
SET account_number = CONCAT('000000', account_number_last4)
WHERE account_number IS NULL AND account_number_last4 IS NOT NULL;

-- Fallback for empty
UPDATE company_payout_profiles
SET account_number = '0000000000'
WHERE account_number IS NULL;

ALTER TABLE company_payout_profiles
    MODIFY COLUMN account_number VARCHAR(50) NOT NULL,
    DROP COLUMN account_number_ciphertext,
    DROP COLUMN account_number_nonce,
    DROP COLUMN encryption_key_version,
    DROP COLUMN account_number_last4;

ALTER TABLE withdrawal_requests
    ADD COLUMN account_number_snapshot VARCHAR(50) NULL AFTER bank_name_snapshot;

-- Populate snapshot if last4 snapshot exists
UPDATE withdrawal_requests
SET account_number_snapshot = CONCAT('000000', account_number_last4_snapshot)
WHERE account_number_snapshot IS NULL AND account_number_last4_snapshot IS NOT NULL;

-- Fallback for empty
UPDATE withdrawal_requests
SET account_number_snapshot = '0000000000'
WHERE account_number_snapshot IS NULL;

ALTER TABLE withdrawal_requests
    MODIFY COLUMN account_number_snapshot VARCHAR(50) NOT NULL,
    DROP COLUMN account_number_ciphertext_snapshot,
    DROP COLUMN account_number_nonce_snapshot,
    DROP COLUMN encryption_key_version_snapshot,
    DROP COLUMN account_number_last4_snapshot;
