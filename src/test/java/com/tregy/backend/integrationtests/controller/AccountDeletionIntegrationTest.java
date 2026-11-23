package com.tregy.backend.integrationtests.controller;

import com.tregy.backend.integrationtests.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import com.tregy.backend.service.AccountPurgeService;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Account deletion (ADR 0009): a grace period, then everything of the user goes and nothing of anyone else. */
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
    @Autowired AccountPurgeService purge;
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

    /** Asks for deletion with the right password and expects it to be scheduled (202). */
    private void requestDeletion(Account a) throws Exception {
        mvc.perform(delete("/api/v1/me").header("Authorization", a.auth()).contentType("application/json")
                .content(deleteBody(PASSWORD))).andExpect(status().isAccepted());
    }

    /** Pretends the request was made this many days ago, then runs the daily purge. */
    private int purgeAfterDays(Account a, int days) {
        jdbc.update("update app_user set deletion_requested_at = now() - make_interval(days => ?) where id = ?",
                days, a.userId());
        return purge.purgeDue();
    }

    private boolean pending(Account a) {
        return jdbc.queryForObject("select deletion_requested_at is not null from app_user where id = ?",
                Boolean.class, a.userId());
    }

    private void login(Account a, String password, int expected) throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", a.email(), "password", password,
                        "installationId", "install-" + a.email())))).andExpect(status().is(expected));
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
    void shouldKeepEverythingDuringTheGracePeriodThenDeleteEveryRowThatBelongsToTheAccountAndNothingElse() throws Exception {
        Account gone = account("gone");
        Account kept = account("kept");

        String body = mvc.perform(delete("/api/v1/me").header("Authorization", gone.auth()).contentType("application/json")
                        .content(deleteBody(PASSWORD)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.deletesAt").exists()).andReturn().getResponse().getContentAsString();
        assertNotNull(json.readTree(body).get("deletesAt"));

        // Asking only schedules it: the data is still there, and so is the request time.
        assertTrue(pending(gone));
        for (String table : USER_TABLES.keySet()) {
            if (!table.equals("email_verification_token")) assertEquals(1, rows(table, gone.userId()), "during grace: " + table);
        }
        // Inside the grace period the purge leaves it alone.
        assertEquals(0, purgeAfterDays(gone, 13));
        assertEquals(1, rows("app_user", gone.userId()));

        assertEquals(1, purgeAfterDays(gone, 15));
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
    void shouldEndEverySessionAtOnceWhenDeletionIsRequested() throws Exception {
        Account a = account("tokens");
        requestDeletion(a);

        mvc.perform(get("/api/v1/me").header("Authorization", a.auth())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json")
                .content(json.writeValueAsString(Map.of("refreshToken", a.refreshToken()))))
                .andExpect(status().isUnauthorized());
        // A second request with the old token is simply unauthenticated.
        mvc.perform(delete("/api/v1/me").header("Authorization", a.auth()).contentType("application/json")
                .content(deleteBody(PASSWORD))).andExpect(status().isUnauthorized());
        // A wrong password does not sign in and does not cancel anything.
        login(a, "not-the-password", 401);
        assertTrue(pending(a));
    }

    @Test
    void shouldCancelTheDeletionWhenTheOwnerSignsInAgainWithinTheGracePeriod() throws Exception {
        Account a = account("cancel");
        requestDeletion(a);
        assertTrue(pending(a));

        String body = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("email", a.email(), "password", PASSWORD,
                                "installationId", "install-" + a.email()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletionCancelled").value(true)).andReturn().getResponse().getContentAsString();
        String newAuth = "Bearer " + json.readTree(body).get("accessToken").asString();

        assertEquals(false, pending(a));
        // Everything is as it was (signing in issued one more refresh token, so that table is not compared),
        // and the purge no longer touches the account.
        for (String table : USER_TABLES.keySet()) {
            if (!table.equals("email_verification_token") && !table.equals("refresh_token")) {
                assertEquals(1, rows(table, a.userId()), table);
            }
        }
        assertEquals(0, purge.purgeDue());
        mvc.perform(get("/api/v1/me").header("Authorization", newAuth)).andExpect(status().isOk());
        // An ordinary sign-in reports that nothing was cancelled.
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("email", a.email(), "password", PASSWORD,
                                "installationId", "install-" + a.email()))))
                .andExpect(jsonPath("$.deletionCancelled").value(false));
    }

    @Test
    void shouldNotDeleteAnAccountWhoseOwnerSignedInAfterTheGracePeriodButBeforeThePurgeRan() throws Exception {
        Account a = account("late");
        requestDeletion(a);
        jdbc.update("update app_user set deletion_requested_at = now() - interval '20 days' where id = ?", a.userId());
        login(a, PASSWORD, 200); // signs in just before the job: cancels
        assertEquals(0, purge.purgeDue());
        assertEquals(1, rows("app_user", a.userId()));
    }

    @Test
    void shouldHoldTheEmailDuringTheGracePeriodThenLetItRegisterAgainAndStartEmpty() throws Exception {
        Account a = account("again");
        requestDeletion(a);

        String registration = json.writeValueAsString(Map.of("email", a.email(), "password", PASSWORD,
                "uiLanguage", "it", "installationId", "install-" + a.email()));
        // The account still exists until the grace period ends, so the email is taken.
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(registration))
                .andExpect(status().isConflict());

        assertEquals(1, purgeAfterDays(a, 15));
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(registration))
                .andExpect(status().isCreated());
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

        requestDeletion(admin);
        assertEquals(1, purgeAfterDays(admin, 15));

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
