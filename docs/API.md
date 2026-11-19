# Tregy API (v1)

Base path: `/api/v1`. JSON, camelCase, ISO-8601 UTC instants. Contract rules: `AGENTS.md` and `../docs/PLAN.md` section 10. Local base URL: `http://localhost:8092`.

Every endpoint needs a JWT (`Authorization: Bearer <accessToken>`) except those marked **Public**. A user reaches only their own data. Anything else returns 404.

## Errors

Every non-2xx response has this shape:

```json
{
  "timestamp": "2026-11-19T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/v1/me/profile",
  "requestId": "6b1f...",
  "fieldErrors": { "gateSize": "must be less than or equal to 5" }
}
```

`code` is a stable string clients switch on; `message` is for humans; `fieldErrors` appears only for validation failures. Codes in use:

| Code | Status | Meaning |
|---|---|---|
| `UNAUTHENTICATED` | 401 | Missing, invalid or expired access token |
| `INVALID_CREDENTIALS` | 401 | Wrong email or password |
| `INVALID_REFRESH_TOKEN` | 401 | Refresh token unknown, expired, revoked or reused; sign in again |
| `VALIDATION_FAILED` | 400 | Body failed validation (see `fieldErrors`) |
| `INVALID_TOKEN` | 400 | Verification or reset link invalid, used or expired |
| `INVALID_DIRECTION` | 400 | Source and target language are the same |
| `UNKNOWN_THEME` | 400 | A theme code does not exist or is not selectable |
| `PROFILE_NOT_FOUND` | 404 | The caller has no learning profile yet (finish onboarding) |
| `EMAIL_ALREADY_EXISTS` | 409 | An account with this email exists |
| `STALE_WRITE` | 409 | The row changed since it was loaded; reload and retry |
| `CONFLICT` | 409 | Database constraint violated |
| `RATE_LIMITED` | 429 | Too many requests; see `Retry-After` |

## Health

### GET /api/v1/health

**Public.** Liveness check; does not touch the database (Docker uses `/actuator/health`, which does).

```json
{ "status": "UP" }
```

## Accounts and sessions

Passwords are 12 to 72 UTF-8 bytes. Emails are normalised (trimmed, lower-cased). `installationId` is a random id the app generates once per install. Access tokens last 15 minutes; refresh tokens 30 days and rotate on every use (reuse is detected and ends the session family).

### POST /api/v1/auth/register

**Public.** Rate limited.

```json
{ "email": "ada@example.com", "password": "correct-horse-battery", "uiLanguage": "it", "installationId": "3f2c..." }
```

`201` returns the user (no tokens; sign in next):

```json
{ "id": "…", "email": "ada@example.com", "uiLanguage": "it", "platformRole": "USER", "emailVerifiedAt": null }
```

Errors: `EMAIL_ALREADY_EXISTS` (409), `VALIDATION_FAILED` (400), `RATE_LIMITED` (429). `uiLanguage` is `en` or `it`.

### POST /api/v1/auth/login

**Public.** Rate limited. Body `{ "email", "password", "installationId" }`. Login works before email verification.

`200`:

```json
{
  "accessToken": "eyJ…", "accessTokenExpiresAt": "2026-11-19T10:15:00Z",
  "refreshToken": "…", "refreshTokenExpiresAt": "2026-12-19T10:00:00Z",
  "user": { "id": "…", "email": "ada@example.com", "uiLanguage": "it", "platformRole": "USER", "emailVerifiedAt": null }
}
```

Errors: `INVALID_CREDENTIALS` (401), `RATE_LIMITED` (429).

### POST /api/v1/auth/refresh

**Public.** Rate limited. Body `{ "refreshToken" }`. Returns the same shape as login with a **new** refresh token; the old one stops working after a short reuse grace period. Error: `INVALID_REFRESH_TOKEN` (401).

### POST /api/v1/auth/logout

**Public.** Body `{ "refreshToken" }`. Revokes that session. `204`.

### POST /api/v1/auth/logout-all

Any user. Revokes every session and invalidates all access tokens. `204`.

### POST /api/v1/auth/verify-email

**Public.** Body `{ "token" }` (from the emailed link). `204`. Error: `INVALID_TOKEN` (400).

### POST /api/v1/auth/resend-verification

**Public.** Rate limited. Body `{ "email" }`. Always `202`, whether or not the account exists.

### POST /api/v1/auth/forgot-password

**Public.** Rate limited. Body `{ "email" }`. Always `202`.

### POST /api/v1/auth/reset-password

**Public.** Body `{ "token", "newPassword" }`. Ends every session. `204`. Error: `INVALID_TOKEN` (400).

### GET /api/v1/me

Any user. Returns the user object shown above.

### PATCH /api/v1/me

Any user. Body `{ "uiLanguage": "en" | "it" }` (field optional). Returns the user.

## Learning profile and themes

### GET /api/v1/me/profile

Any user. The caller's learning profile, or `404 PROFILE_NOT_FOUND` until onboarding has saved one.

```json
{
  "sourceLang": "it", "targetLang": "en", "level": "BEGINNER",
  "gateSize": 3, "unlockMinutes": 15,
  "creepMode": "GENTLE", "creepStep": 0, "successfulUnlocks": 0,
  "updatedAt": "2026-11-19T10:00:00Z"
}
```

### PUT /api/v1/me/profile

Any user. Creates or updates the caller's profile.

```json
{ "sourceLang": "it", "targetLang": "en", "level": "BEGINNER", "gateSize": 3, "unlockMinutes": 15, "creepMode": "GENTLE" }
```

| Field | Rule |
|---|---|
| `sourceLang`, `targetLang` | `en` or `it`, and different (`INVALID_DIRECTION`) |
| `level` | `BEGINNER`, `INTERMEDIATE`, `ADVANCED` |
| `gateSize` | 1 to 5; omitted keeps the stored value (default 3) |
| `unlockMinutes` | 5 to 60; omitted keeps the stored value (default 15) |
| `creepMode` | `OFF`, `GENTLE`, `STEADY`; omitted keeps the stored value (default `GENTLE`); `OFF` resets `creepStep` to 0 |
| `creepStep` | 0 to 3, device-owned counter; omitted keeps the stored value |
| `successfulUnlocks` | 0 and up, device-owned counter; omitted keeps the stored value |

Returns the profile. Changing direction or level does not delete learning history.

### GET /api/v1/themes

Any user. The selectable theme catalogue in display order (`general` is the implicit base and is not listed).

```json
[ { "code": "travel", "nameEn": "Travel", "nameIt": "Viaggi" } ]
```

### GET /api/v1/me/themes

Any user. Codes of the caller's chosen themes, sorted: `["food", "travel"]`.

### PUT /api/v1/me/themes

Any user. Replaces the caller's themes. Body `{ "themes": ["travel", "food"] }` (1 to 12 codes). Returns the new sorted list. Error: `UNKNOWN_THEME` (400).

## Rate limits

`/auth/login`, `/auth/register`, `/auth/refresh`, `/auth/forgot-password` and `/auth/resend-verification` allow 10 requests per address per 10 minutes, plus a per-email limit on login, register and recovery. Exceeding returns `429 RATE_LIMITED` with `Retry-After`.
