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

### DELETE /api/v1/me

Any user, for their own account only (there is no id in the path). Deletes the account and everything that belongs to it: devices, refresh and recovery tokens, the learning profile and themes, all schedule state and all sync events. Nothing about the user is kept (no copy of the email or its hash), so the email can register again at once. Content and generation runs are not user data and stay; for runs started by an administrator the creator link is cleared. Rules: ADR 0009.

Body (the caller's own password is required, so a stolen access token alone cannot delete the account):

```json
{ "password": "correct-horse-battery" }
```

`204` with no body on success. Every access token and refresh token of the account stops working at once; a second call gets `401`. Errors: `400` (no password), `401 INVALID_CREDENTIALS` (wrong password, nothing deleted; also `UNAUTHENTICATED` without a valid token), `429 RATE_LIMITED` (5 password attempts per account per 10 minutes, with `Retry-After`).

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

## Progress sync

The phone is authoritative at gate time; the server stores progress so a reinstall or a second phone can restore it (ADR 0007). No answers, recordings, app lists or gate contents are ever sent.

### POST /api/v1/sync/events

Any user. Body `{ "events": [ ... ] }`, 1 to 100 events, each the item's state after an answer:

```json
{ "clientEventId": "uuid", "itemId": "gen-b-001", "stage": 2, "dueOn": "2026-05-13",
  "learnedAt": "2026-05-11T10:00:00Z", "lastResult": "correct", "lastCorrectOn": "2026-05-11",
  "updatedAt": "2026-05-11T10:00:00Z" }
```

`stage` is 0 to 6; `lastResult` is `correct`, `wrong` or null; `dueOn`, `learnedAt`, `lastCorrectOn` may be null; `itemId` matches `[A-Za-z0-9_.:-]{1,64}`. Returns `200 { "results": [ { "clientEventId", "status", "code" } ] }` in request order. `status` is `ACCEPTED`, `DUPLICATE` (event id already applied; nothing changes) or `REJECTED` with `code` `INVALID_EVENT_ID` or `INVALID_EVENT`. One bad event does not stop the others. Conflict rules: the later `updatedAt` wins the schedule fields; `learnedAt` keeps the earliest value and never clears. A whole batch is `400 VALIDATION_FAILED` if empty or over 100 events. Limited to 60 calls per user per 10 minutes (`429 RATE_LIMITED`).

### GET /api/v1/me/progress

Any user. Query `page` (default 0) and `size` (default 200, capped at 500). Returns `{ "learned", "total", "page", "size", "items": [ { "itemId", "stage", "dueOn", "learnedAt", "lastResult", "lastCorrectOn", "updatedAt" } ] }`, items sorted by `itemId`. `learned` counts every item with a `learnedAt`. Only the caller's own rows are ever returned.

## Content generation (operators)

Only `PLATFORM_ADMIN` accounts. Anyone else gets `403`; no token gets `401`. Generation is a server-side batch job: the app never calls a model (ADR 0003). Only a fake provider exists for now (no network, no key).

### POST /api/v1/platform/generation-runs

Body `{ "sourceLang": "it", "targetLang": "en", "level": "BEGINNER|INTERMEDIATE|ADVANCED", "theme": "food", "kind": "WORD|PHRASE|SENTENCE", "count": 1-500 }`. Generates, validates in code and stores items for that bucket as `VALIDATED`; re-running tops up and never duplicates. Returns `201` with the run: counts (`generatedCount`, `acceptedCount`, `rejectedCount`), `rejections` (reason code to count), `status` (`COMPLETED`, `STOPPED_BUDGET`, `FAILED`), `promptVersion`, `modelId`, `costMicroUsd`. Errors (400): `INVALID_DIRECTION`, `UNKNOWN_THEME`, `RUN_TOO_LARGE` (over `MAX_ITEMS_PER_RUN`), `VALIDATION_FAILED`. A spent monthly budget returns a run with `STOPPED_BUDGET` and nothing generated. Rejection reasons: `MISSING_FIELD`, `BAD_POS`, `TOO_SHORT`, `TOO_LONG`, `WRONG_LANGUAGE`, `DUPLICATE`, `NEAR_DUPLICATE`, `BAD_DISTRACTORS`, `BAD_ACCEPTED_VARIANTS`, `UNSAFE`, `TOO_HARD`.

### GET /api/v1/platform/generation-runs

Most recent runs first; `limit` (default 20, max 100).

### GET /api/v1/platform/content-items/review

Operators. Items waiting to be published (`VALIDATED`), newest first; `page`, `size` (default 50, max 200).

### POST /api/v1/platform/content/publish

Operators. Body `{ "sourceLang", "targetLang", "level", "theme" }`. Publishes every validated item in that bucket; returns `{ "published": n }` (0 when there is nothing to publish, so it is safe to repeat). Published items are never edited.

### POST /api/v1/platform/content-items/{id}/withdraw

Operators. Takes an item out of circulation (only its status changes; its text and difficulty never do). `404 NOT_FOUND` for an unknown id. Phones that hold the pack drop the item on their next download.

### GET /api/v1/platform/content-items/export

Operators. A random sample as `text/csv` (UTF-8, attachment) for a native speaker to check in a spreadsheet. Optional filters `sourceLang`, `targetLang`, `level`, `theme`, `status`; `limit` (default 50, max 200). Columns: id, source_lang, target_lang, level, theme, kind, difficulty, status, source_text, target_text, accepted, and empty `verdict` and `notes` columns for the reviewer. A cell that starts with `=`, `+`, `-` or `@` gets a leading apostrophe so it can never run as a spreadsheet formula.

## Content packs (phones)

Any signed-in user. Published content only, no user data.

### GET /api/v1/content/packs

Query `sourceLang`, `targetLang`, `levels` (comma separated, 1 to 3 of `BEGINNER`, `INTERMEDIATE`, `ADVANCED`) and `themes` (comma separated, up to 20; the `general` pack is always included). Returns `[ { "packId": "it-en-BEGINNER-food", "theme", "level", "version", "itemCount" } ]`. `version` is milliseconds since the epoch of the pack's latest publish or withdrawal; it only rises, so a phone downloads again only when it is higher than the one it holds. Packs with no published items are left out. Errors (400): `INVALID_DIRECTION`, `INVALID_PACK_QUERY`.

### GET /api/v1/content/packs/{packId}

Returns `{ "packId", "version", "theme", "level", "languages": ["it","en"], "entries": [ { "id", "pos", "difficulty", "text": { "it": "...", "en": "..." }, "accepted": { "en": ["..."] } } ] }`. `404 NOT_FOUND` if the id is malformed or the pack has no published items. Entry ids are stable, so progress on an item survives a pack update.

## Rate limits

`/auth/login`, `/auth/register`, `/auth/refresh`, `/auth/forgot-password` and `/auth/resend-verification` allow 10 requests per address per 10 minutes, plus a per-email limit on login, register and recovery. Exceeding returns `429 RATE_LIMITED` with `Retry-After`. `DELETE /me` allows 5 password attempts per account per 10 minutes.
