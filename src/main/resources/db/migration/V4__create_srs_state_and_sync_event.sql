-- Per-user spaced-repetition state, one row per learning item. item_id is the stable id of the
-- content entry on the device; it has no foreign key yet because the server-side item catalogue
-- arrives with the content engine (phase 5), which will add item-existence validation.
CREATE TABLE public.srs_state (
    user_id VARCHAR(36) NOT NULL REFERENCES public.app_user(id) ON DELETE CASCADE,
    item_id VARCHAR(64) NOT NULL,
    stage INTEGER NOT NULL,
    due_on DATE,
    learned_at TIMESTAMPTZ,
    last_result VARCHAR(10),
    last_correct_on DATE,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, item_id),
    CONSTRAINT ck_srs_stage CHECK (stage BETWEEN 0 AND 6),
    CONSTRAINT ck_srs_last_result CHECK (last_result IS NULL OR last_result IN ('correct','wrong'))
);

-- Remembers which client events were applied, so a retried batch changes nothing.
CREATE TABLE public.sync_event (
    user_id VARCHAR(36) NOT NULL REFERENCES public.app_user(id) ON DELETE CASCADE,
    client_event_id VARCHAR(36) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, client_event_id)
);
