package com.tregy.backend.integrationtests.controller;

import com.tregy.backend.integrationtests.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Account deletion (ADR 0009): everything of the user goes, nothing of anyone else does. */
class AccountDeletionIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";
    private static final AtomicInteger COUNTER = new AtomicInteger();
    // Every table that holds rows of one user, with the column that names the owner.
    private static final Map<String, String> USER_TABLES = Map.of(
            "app_user", "id", "device", "user_id", "refresh_token", "user_id",
            "email_verification_token", "user_id", "learner_profile", "user_id",
            "learner_theme", "user_id", "srs_state", "user_id", "sync_event", "user_id");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final JsonMapper json = JsonMapper.builder().build();

    private record Account(String email, String userId, String auth, String refreshToken) {}

    /** Registers, signs in and gives the account a profile, themes and a synced item. */
    private Account account(String name) throws Exception {
        String email = name + COUNTER.incrementAndGet() + "@example.com";
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD,
                        "uiLanguage", "it", "installationId", "install-" + email)))).andExpect(status().isCreated());
        String body = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD,
                                "installationId", "install-" + email))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String auth = "Bearer " + json.readTree(body).get("accessToken").asString();
        String refresh = json.readTree(body).get("refreshToken").asString();
        String userId = jdbc.queryForObject("select id from app_user where email = ?", String.class, email);

        mvc.perform(put("/api/v1/me/profile").header("Authorization", auth).contentType("application/json")
                .content(json.writeValueAsString(Map.of("sourceLang", "it", "targetLang", "en", "level", "BEGINNER"))))
                .andExpect(status().is2xxSuccessful());
        mvc.perform(put("/api/v1/me/themes").header("Authorization", auth).contentType("application/json")
                .content("{\"themes\":[\"travel\"]}")).andExpect(status().is2xxSuccessful());
        Map<String, Object> event = Map.of("clientEventId", UUID.randomUUID().toString(), "itemId", "gen-b-001",
                "stage", 1, "dueOn", "2026-05-11", "lastResult", "correct", "lastCorrectOn", "2026-05-10",
                "updatedAt", "2026-05-10T10:00:00Z");
        mvc.perform(post("/api/v1/sync/events").header("Authorization", auth).contentType("application/json")
                .content(json.writeValueAsString(Map.of("events", List.of(event))))).andExpect(status().isOk());
        return new Account(email, userId, auth, refresh);
    }

    private int rows(String table, String userId) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + USER_TABLES.get(table) + " = ?",
                Integer.class, userId);
    }

    private String deleteBody(String password) throws Exception {
        return json.writeValueAsString(Map.of("password", password));
    }

    @Test
    void shouldRequireAuthentication() throws Exception {
        mvc.perform(delete("/api/v1/me").contentType("application/json").content(deleteBody(PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectARequestWithoutAPassword() throws Exception {
        Account a = account("nopass");
        mvc.perform(delete("/api/v1/me").header("Authorization", a.auth()).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        assertEquals(1, rows("app_user", a.userId()));
    }

    @Test
    void shouldRefuseAWrongPasswordAndDeleteNothing() throws Exception {
        Account a = account("wrong");
        mvc.perform(delete("/api/v1/me").header("Authorization", a.auth()).contentType("application/json")
                        .content(deleteBody("not-the-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        for (String table : USER_TABLES.keySet()) {
            if (!table.equals("email_verification_token")) assertEquals(1, rows(table, a.userId()), table);
        }
    }

    @Test
    void shouldDeleteTheAccountAndEveryRowThatBelongsToItAndNothingElse() throws Exception {
        Account gone = account("gone");
        Account kept = account("kept");
        for (String table : USER_TABLES.keySet()) {
            if (!table.equals("email_verification_token")) {
                assertEquals(1, rows(table, gone.userId()), "before: " + table);
            }
        }

        mvc.perform(delete("/api/v1/me").header("Authorization", gone.auth()).contentType("application/json")
                .content(deleteBody(PASSWORD))).andExpect(status().isNoContent());

        for (String table : USER_TABLES.keySet()) {
            assertEquals(0, rows(table, gone.userId()), "after: " + table);
        }
        // Another user's data is untouched.
        for (String table : USER_TABLES.keySet()) {
            if (!table.equals("email_verification_token")) assertEquals(1, rows(table, kept.userId()), "kept: " + table);
        }
        mvc.perform(get("/api/v1/me").header("Authorization", kept.auth())).andExpect(status().isOk());
    }

    @Test
    void shouldStopEveryTokenOfADeletedAccountWorking() throws Exception {
        Account a = account("tokens");
        mvc.perform(delete("/api/v1/me").header("Authorization", a.auth()).contentType("application/json")
                .content(deleteBody(PASSWORD))).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/me").header("Authorization", a.auth())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json")
                .content(json.writeValueAsString(Map.of("refreshToken", a.refreshToken()))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("email", a.email(), "password", PASSWORD,
                                "installationId", "install-" + a.email()))))
                .andExpect(status().isUnauthorized());
        // A second delete with the old token is simply unauthenticated.
        mvc.perform(delete("/api/v1/me").header("Authorization", a.auth()).contentType("application/json")
                .content(deleteBody(PASSWORD))).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldLetTheSameEmailRegisterAgainAndStartEmpty() throws Exception {
        Account a = account("again");
        mvc.perform(delete("/api/v1/me").header("Authorization", a.auth()).contentType("application/json")
                .content(deleteBody(PASSWORD))).andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", a.email(), "password", PASSWORD,
                        "uiLanguage", "it", "installationId", "install-" + a.email())))).andExpect(status().isCreated());
        String newId = jdbc.queryForObject("select id from app_user where email = ?", String.class, a.email());
        assertNotEquals(a.userId(), newId);
        for (String table : USER_TABLES.keySet()) {
            if (!table.equals("app_user") && !table.equals("email_verification_token") && !table.equals("device")
                    && !table.equals("refresh_token")) {
                assertEquals(0, rows(table, newId), "fresh account has no " + table);
            }
        }
    }

    @Test
    void shouldDeleteAnAdministratorWhoCreatedGenerationRunsAndKeepTheRuns() throws Exception {
        Account admin = account("admin");
        jdbc.update("update app_user set platform_role = 'PLATFORM_ADMIN' where id = ?", admin.userId());
        String runId = UUID.randomUUID().toString();
        jdbc.update("""
                insert into generation_run (id, source_lang, target_lang, level, theme, kind, requested_count,
                    status, prompt_version, model_id, created_by, created_at)
                values (?, 'it', 'en', 'BEGINNER', 'travel', 'WORD', 5, 'COMPLETED', 'p1', 'fake', ?, now())
                """, runId, admin.userId());

        mvc.perform(delete("/api/v1/me").header("Authorization", admin.auth()).contentType("application/json")
                .content(deleteBody(PASSWORD))).andExpect(status().isNoContent());

        assertEquals(0, rows("app_user", admin.userId()));
        assertEquals(1, jdbc.queryForObject("select count(*) from generation_run where id = ?", Integer.class, runId));
        assertNull(jdbc.queryForObject("select created_by from generation_run where id = ?", String.class, runId));
    }

    @Test
    void shouldLimitPasswordAttemptsPerAccountAndKeepTheAccount() throws Exception {
        Account a = account("limit");
        for (int i = 0; i < 5; i++) {
            mvc.perform(delete("/api/v1/me").header("Authorization", a.auth()).contentType("application/json")
                    .content(deleteBody("guess-" + i))).andExpect(status().isUnauthorized());
        }
        // Even the right password is refused once the limit is reached.
        mvc.perform(delete("/api/v1/me").header("Authorization", a.auth()).contentType("application/json")
                        .content(deleteBody(PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        assertEquals(1, rows("app_user", a.userId()));
    }
}
