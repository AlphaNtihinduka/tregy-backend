# tregy-backend

Spring Boot API for Tregy, a language-learning app lock (English and Italian). Planning docs, decisions and agent rules live in the parent `tregy/` folder (`AGENTS.md`, `docs/PLAN.md`).

## Run locally

```
cp .env.example .env   # set POSTGRES_PASSWORD and later JWT_SECRET
docker compose up --build
curl http://localhost:8092/api/v1/health
```

Host port 8092 maps to container port 8081. Never run `docker compose down -v` (deletes the database volume).

## Test

`./mvnw verify` needs JDK 25 and Docker (Testcontainers starts PostgreSQL 17).

API contract: `docs/API.md`.
