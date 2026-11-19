# tregy-backend agent instructions

Repo-local summary for tools that only read inside this repo. The full rules, plan and decisions are in the parent `tregy/` folder: `../AGENTS.md`, `../docs/PLAN.md`, `../docs/decisions/`. Read `../.claude/PROJECT_STATE.md` at the start of a session.

* Spring Boot 4, Java 25, Maven wrapper, PostgreSQL 17, Spring Data JPA, Flyway, MapStruct, Lombok. Package `com.tregy.backend`; layered: `config`, `controller`, `dto/request`, `dto/response`, `exception`, `mapper`, `model`, `model/enums`, `repository`, `service`, `service/impl`, `service/support`.
* Every schema change is a new Flyway migration `V<n>__snake_description.sql`. Never edit an applied one. `ddl-auto=validate`.
* All endpoints under `/api/v1`; only auth and health are public. A user reaches only their own data, resolved in the repository query; other users' data returns 404.
* No user data in LLM prompts. Generated content is validated before publishing; published items are immutable.
* Never log answers, emails, tokens or package names tied to a person. Never commit `.env`.
* Local: `docker compose up --build` (API on host port 8092). Tests: `./mvnw verify` (JDK 25 + Docker for Testcontainers).
* Document every endpoint in `docs/API.md` when it ships. Never commit unless asked.
