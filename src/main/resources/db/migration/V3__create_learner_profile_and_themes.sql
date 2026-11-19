-- Themes are a small catalogue the learner picks from. GENERAL is the implicit base and is
-- not selectable. Names are stored per UI language.
CREATE TABLE public.theme (
    code VARCHAR(30) PRIMARY KEY,
    name_en VARCHAR(60) NOT NULL,
    name_it VARCHAR(60) NOT NULL,
    selectable BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL,
    CONSTRAINT ck_theme_code CHECK (code ~ '^[a-z][a-z0-9_]*$')
);

INSERT INTO public.theme (code, name_en, name_it, selectable, sort_order) VALUES
    ('general', 'Everyday', 'Quotidiano', FALSE, 0),
    ('travel',  'Travel',   'Viaggi',     TRUE,  10),
    ('work',    'Work',     'Lavoro',     TRUE,  20),
    ('dating',  'Dating',   'Incontri',   TRUE,  30),
    ('food',    'Food',     'Cibo',       TRUE,  40);

-- One row per user: what they study and how the gate behaves. successful_unlocks and
-- creep_step are device-owned counters synced here (phase 7); the server only range-checks.
CREATE TABLE public.learner_profile (
    user_id VARCHAR(36) PRIMARY KEY REFERENCES public.app_user(id) ON DELETE CASCADE,
    source_lang VARCHAR(5) NOT NULL,
    target_lang VARCHAR(5) NOT NULL,
    level VARCHAR(20) NOT NULL,
    gate_size INTEGER NOT NULL DEFAULT 3,
    unlock_minutes INTEGER NOT NULL DEFAULT 15,
    creep_mode VARCHAR(20) NOT NULL DEFAULT 'GENTLE',
    creep_step INTEGER NOT NULL DEFAULT 0,
    successful_unlocks INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_learner_direction CHECK (
        (source_lang = 'it' AND target_lang = 'en') OR (source_lang = 'en' AND target_lang = 'it')),
    CONSTRAINT ck_learner_level CHECK (level IN ('BEGINNER','INTERMEDIATE','ADVANCED')),
    CONSTRAINT ck_learner_gate_size CHECK (gate_size BETWEEN 1 AND 5),
    CONSTRAINT ck_learner_unlock_minutes CHECK (unlock_minutes BETWEEN 5 AND 60),
    CONSTRAINT ck_learner_creep_mode CHECK (creep_mode IN ('OFF','GENTLE','STEADY')),
    CONSTRAINT ck_learner_creep_step CHECK (creep_step BETWEEN 0 AND 3),
    CONSTRAINT ck_learner_successful_unlocks CHECK (successful_unlocks >= 0)
);

CREATE TABLE public.learner_theme (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES public.app_user(id) ON DELETE CASCADE,
    theme_code VARCHAR(30) NOT NULL REFERENCES public.theme(code),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_learner_theme UNIQUE (user_id, theme_code)
);
