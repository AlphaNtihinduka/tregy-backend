-- Account deletion (ADR 0009). A generation run records which administrator started it, but it
-- holds no personal data. Deleting that administrator must not be blocked, so the link becomes
-- optional and is cleared when the user is deleted; the run history stays.
ALTER TABLE public.generation_run ALTER COLUMN created_by DROP NOT NULL;
ALTER TABLE public.generation_run DROP CONSTRAINT generation_run_created_by_fkey;
ALTER TABLE public.generation_run
    ADD CONSTRAINT generation_run_created_by_fkey
    FOREIGN KEY (created_by) REFERENCES public.app_user(id) ON DELETE SET NULL;
