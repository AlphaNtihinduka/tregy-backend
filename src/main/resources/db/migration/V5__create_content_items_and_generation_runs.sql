-- Server-side content catalogue (phase 5). Items are written by the generation pipeline, validated
-- in code, and become immutable once published: a correction is a new row, never an edit.
CREATE TABLE public.generation_run (
    id VARCHAR(36) PRIMARY KEY,
    source_lang VARCHAR(5) NOT NULL,
    target_lang VARCHAR(5) NOT NULL,
    level VARCHAR(20) NOT NULL,
    theme VARCHAR(30) NOT NULL REFERENCES public.theme(code),
    kind VARCHAR(10) NOT NULL,
    requested_count INTEGER NOT NULL,
    generated_count INTEGER NOT NULL DEFAULT 0,
    accepted_count INTEGER NOT NULL DEFAULT 0,
    rejected_count INTEGER NOT NULL DEFAULT 0,
    -- JSON object: rejection reason code -> count.
    rejection_summary TEXT NOT NULL DEFAULT '{}',
    status VARCHAR(20) NOT NULL,
    prompt_version VARCHAR(20) NOT NULL,
    model_id VARCHAR(80) NOT NULL,
    input_tokens BIGINT NOT NULL DEFAULT 0,
    output_tokens BIGINT NOT NULL DEFAULT 0,
    -- Estimated cost in millionths of a US dollar, so month totals are exact integers.
    cost_micro_usd BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(36) NOT NULL REFERENCES public.app_user(id),
    created_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    CONSTRAINT ck_run_level CHECK (level IN ('BEGINNER','INTERMEDIATE','ADVANCED')),
    CONSTRAINT ck_run_kind CHECK (kind IN ('WORD','PHRASE','SENTENCE')),
    CONSTRAINT ck_run_status CHECK (status IN ('RUNNING','COMPLETED','STOPPED_BUDGET','FAILED')),
    CONSTRAINT ck_run_requested CHECK (requested_count BETWEEN 1 AND 500)
);

CREATE INDEX ix_generation_run_created ON public.generation_run (created_at);

CREATE TABLE public.content_item (
    id VARCHAR(36) PRIMARY KEY,
    source_lang VARCHAR(5) NOT NULL,
    target_lang VARCHAR(5) NOT NULL,
    level VARCHAR(20) NOT NULL,
    theme VARCHAR(30) NOT NULL REFERENCES public.theme(code),
    kind VARCHAR(10) NOT NULL,
    pos VARCHAR(10) NOT NULL,
    source_text VARCHAR(300) NOT NULL,
    target_text VARCHAR(300) NOT NULL,
    -- JSON arrays of strings. Accepted answers (canonical first) for translate; wrong options for flashcards.
    accepted_variants TEXT NOT NULL,
    distractors TEXT NOT NULL,
    difficulty INTEGER NOT NULL,
    -- Lower-cased, accent-folded target text: the duplicate key within a direction.
    dedupe_key VARCHAR(300) NOT NULL,
    status VARCHAR(12) NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    generation_run_id VARCHAR(36) NOT NULL REFERENCES public.generation_run(id),
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    CONSTRAINT ck_item_level CHECK (level IN ('BEGINNER','INTERMEDIATE','ADVANCED')),
    CONSTRAINT ck_item_kind CHECK (kind IN ('WORD','PHRASE','SENTENCE')),
    CONSTRAINT ck_item_pos CHECK (pos IN ('noun','verb','adj','phrase')),
    CONSTRAINT ck_item_status CHECK (status IN ('VALIDATED','PUBLISHED','WITHDRAWN')),
    CONSTRAINT ck_item_difficulty CHECK (difficulty BETWEEN 1 AND 100)
);

-- One live item per target text in a direction; a withdrawn row frees the text for a corrected version.
CREATE UNIQUE INDEX uk_content_item_live ON public.content_item (source_lang, target_lang, dedupe_key)
    WHERE status <> 'WITHDRAWN';
CREATE INDEX ix_content_item_bucket ON public.content_item (source_lang, target_lang, level, theme, status);
