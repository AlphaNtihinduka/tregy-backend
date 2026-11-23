-- Account deletion with a grace period (ADR 0009, revised). Asking to delete does not remove the
-- account at once: it records when the request was made, revokes every session and waits. Signing in
-- again before the grace period ends clears the column (the deletion is cancelled); a daily job
-- deletes the accounts whose grace period has ended. The partial index keeps that job's query cheap
-- because almost every row has no request.
ALTER TABLE public.app_user ADD COLUMN deletion_requested_at timestamptz;
CREATE INDEX idx_app_user_deletion_requested_at
    ON public.app_user (deletion_requested_at)
    WHERE deletion_requested_at IS NOT NULL;
