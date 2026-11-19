# Tregy API (v1)

Base path: `/api/v1`. JSON, camelCase, ISO-8601 UTC instants. Contract rules: see the AGENTS.md and `../docs/PLAN.md` section 10. Local base URL: `http://localhost:8092`.

## Errors

Every non-2xx response has this shape:

```json
{
  "timestamp": "2026-11-15T10:00:00Z",
  "status": 401,
  "error": "Unauthorized",
  "code": "UNAUTHENTICATED",
  "message": "Sign in required",
  "path": "/api/v1/me"
}
```

`code` is a stable string clients can switch on. `message` is for humans.

## Endpoints

### GET /api/v1/health

Public. Liveness check; does not touch the database (Docker uses `/actuator/health`, which does).

Response `200`:

```json
{ "status": "UP" }
```

Any other `/api/v1/**` path requires a JWT and returns `401 UNAUTHENTICATED` without one. Accounts and sessions arrive in phase 1.
