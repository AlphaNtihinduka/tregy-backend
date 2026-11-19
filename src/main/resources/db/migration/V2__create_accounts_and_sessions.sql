CREATE TABLE public.app_user (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    ui_language VARCHAR(5) NOT NULL DEFAULT 'en',
    platform_role VARCHAR(20) NOT NULL DEFAULT 'USER',
    email_verified_at TIMESTAMPTZ,
    token_version INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_app_user_email UNIQUE (email),
    CONSTRAINT ck_app_user_email_normalized CHECK (email = lower(btrim(email))),
    CONSTRAINT ck_app_user_platform_role CHECK (platform_role IN ('USER','PLATFORM_ADMIN')),
    CONSTRAINT ck_app_user_ui_language CHECK (ui_language IN ('en','it')),
    CONSTRAINT ck_app_user_token_version CHECK (token_version >= 0)
);
CREATE TABLE public.device (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES public.app_user(id) ON DELETE CASCADE,
    installation_id VARCHAR(100) NOT NULL,
    platform VARCHAR(20) NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_device_user_installation UNIQUE (user_id, installation_id),
    CONSTRAINT ck_device_platform CHECK (platform IN ('ANDROID','IOS'))
);
CREATE INDEX ix_device_user_last_seen ON public.device(user_id, last_seen_at DESC);

CREATE TABLE public.refresh_token (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES public.app_user(id) ON DELETE CASCADE,
    device_id VARCHAR(36) NOT NULL REFERENCES public.device(id) ON DELETE CASCADE,
    family_id VARCHAR(36) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by_id VARCHAR(36) REFERENCES public.refresh_token(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_refresh_token_hash CHECK (token_hash ~ '^[0-9a-f]{64}$')
);
CREATE INDEX ix_refresh_token_user_live ON public.refresh_token(user_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_refresh_token_family ON public.refresh_token(family_id);

CREATE TABLE public.email_verification_token (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES public.app_user(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_email_verification_hash CHECK (token_hash ~ '^[0-9a-f]{64}$')
);
CREATE INDEX ix_email_verification_user ON public.email_verification_token(user_id);

CREATE TABLE public.password_reset_token (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES public.app_user(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_password_reset_hash CHECK (token_hash ~ '^[0-9a-f]{64}$')
);
CREATE INDEX ix_password_reset_user ON public.password_reset_token(user_id);
