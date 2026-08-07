-- Migration: Ensure organizer_wallets record exists for any company with payout profile
-- File: V14__ensure_wallet_exists_for_payout_profiles.sql

INSERT INTO organizer_wallets (id, company_id, currency, pending_balance, available_balance, reserved_balance, withdrawn_total, version, created_at, updated_at)
SELECT UUID_TO_BIN(UUID()), cpp.company_id, 'VND', 0.00, 0.00, 0.00, 0.00, 0, NOW(), NOW()
FROM company_payout_profiles cpp
LEFT JOIN organizer_wallets ow ON cpp.company_id = ow.company_id
WHERE ow.id IS NULL;
