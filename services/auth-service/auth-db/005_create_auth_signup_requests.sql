CREATE TABLE IF NOT EXISTS auth_signup_requests (
    id UUID PRIMARY KEY,
    first_name VARCHAR(64) NOT NULL,
    last_name VARCHAR(64) NOT NULL,
    username VARCHAR(254) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    dob DATE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'NEW',
    review_notes TEXT,
    rejection_reason TEXT,
    approved_account_reference UUID,
    approved_auth_user_id UUID,
    reviewed_by VARCHAR(64),
    reviewed_on TIMESTAMPTZ,
    created_on TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_on TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_auth_signup_requests_status CHECK (status IN ('NEW', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_auth_signup_requests_username_email CHECK (
      username ~* '^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$'
    ),
    CONSTRAINT fk_auth_signup_requests_approved_user FOREIGN KEY (approved_auth_user_id)
        REFERENCES auth_users(id)
        ON DELETE SET NULL,
    CONSTRAINT chk_auth_signup_requests_approved_state CHECK (
      (status <> 'APPROVED') OR
      (
        reviewed_on IS NOT NULL AND
        reviewed_by IS NOT NULL AND
        approved_account_reference IS NOT NULL AND
        approved_auth_user_id IS NOT NULL
      )
    ),
    CONSTRAINT chk_auth_signup_requests_rejected_state CHECK (
      (status <> 'REJECTED') OR
      (
        reviewed_on IS NOT NULL AND
        reviewed_by IS NOT NULL AND
        rejection_reason IS NOT NULL
      )
    ),
    CONSTRAINT chk_auth_signup_requests_new_state CHECK (
      (status <> 'NEW') OR
      (
        reviewed_on IS NULL AND
        reviewed_by IS NULL AND
        approved_account_reference IS NULL AND
        approved_auth_user_id IS NULL AND
        rejection_reason IS NULL
      )
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_auth_signup_requests_username_active
  ON auth_signup_requests (lower(username))
  WHERE status IN ('NEW', 'APPROVED');

CREATE INDEX IF NOT EXISTS idx_auth_signup_requests_status_created_on
  ON auth_signup_requests (status, created_on DESC);
