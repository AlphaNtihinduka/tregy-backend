-- Baseline. Phase 0 has no tables yet; phase 1 adds app_user, refresh_token and device
-- in V2. This file exists so Flyway initialises its history table against a real
-- PostgreSQL 17 from the first run, and so integration tests prove the migration path.
SELECT 1;
