package com.tregy.backend.integrationtests.controller;

import com.tregy.backend.integrationtests.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GenerationIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";
    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void clean() {
        jdbc.update("delete from content_item");
        jdbc.update("delete from generation_run");
        jdbc.update("delete from sync_event");
        jdbc.update("delete from srs_state");
        jdbc.update("delete from learner_theme");
        jdbc.update("delete from learner_profile");
        jdbc.update("delete from refresh_token");
        jdbc.update("delete from device");
        jdbc.update("delete from app_user");
    }

    private String signIn(String name, boolean admin) throws Exception {
        String email = name + COUNTER.incrementAndGet() + "@example.com";
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD,
                        "uiLanguage", "it", "installationId", "install-" + email)))).andExpect(status().isCreated());
        if (admin) {
            jdbc.update("update app_user set platform_role = 'PLATFORM_ADMIN' where email = ?", email);
        }
        String body = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD,
                                "installationId", "install-" + email))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("accessToken").asString();
    }

    private String run(String theme, String kind, int count) {
        return json.writeValueAsString(Map.of("sourceLang", "it", "targetLang", "en", "level", "INTERMEDIATE",
                "theme", theme, "kind", kind, "count", count));
    }

    @Test
    void shouldRefuseNormalUsersAndTheUnauthenticated() throws Exception {
        mvc.perform(post("/api/v1/platform/generation-runs").contentType("application/json").content(run("food", "WORD", 5)))
                .andExpect(status().isUnauthorized());
        String user = signIn("user", false);
        mvc.perform(post("/api/v1/platform/generation-runs").header("Authorization", user)
                        .contentType("application/json").content(run("food", "WORD", 5)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/platform/generation-runs").header("Authorization", user))
                .andExpect(status().isForbidden());
        assertEquals(0, jdbc.queryForObject("select count(*) from content_item", Integer.class));
    }

    @Test
    void shouldGenerateValidatedItemsAndReportTheRun() throws Exception {
        String admin = signIn("admin", true);
        mvc.perform(post("/api/v1/platform/generation-runs").header("Authorization", admin)
                        .contentType("application/json").content(run("food", "WORD", 5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.acceptedCount").value(5))
                .andExpect(jsonPath("$.modelId").value("fake-model"))
                .andExpect(jsonPath("$.promptVersion").value("fake-1"));
        assertEquals(5, jdbc.queryForObject(
                "select count(*) from content_item where status = 'VALIDATED' and theme = 'food'", Integer.class));
        Integer outOfBand = jdbc.queryForObject("select count(*) from content_item where difficulty > 66", Integer.class);
        assertEquals(0, outOfBand);
        mvc.perform(get("/api/v1/platform/generation-runs").header("Authorization", admin))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void shouldTopUpWithoutDuplicatingWhenARunIsRepeated() throws Exception {
        String admin = signIn("admin", true);
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/platform/generation-runs").header("Authorization", admin)
                    .contentType("application/json").content(run("travel", "WORD", 100))).andExpect(status().isCreated());
        }
        // The fake holds eight travel words; running again adds nothing and never repeats a text.
        assertEquals(8, jdbc.queryForObject("select count(*) from content_item", Integer.class));
        assertEquals(8, jdbc.queryForObject("select count(distinct dedupe_key) from content_item", Integer.class));
    }

    @Test
    void shouldRejectBadRequests() throws Exception {
        String admin = signIn("admin", true);
        for (String body : new String[]{
                json.writeValueAsString(Map.of("sourceLang", "it", "targetLang", "it", "level", "BEGINNER",
                        "theme", "food", "kind", "WORD", "count", 5)),
                json.writeValueAsString(Map.of("sourceLang", "it", "targetLang", "en", "level", "BEGINNER",
                        "theme", "nope", "kind", "WORD", "count", 5)),
                json.writeValueAsString(Map.of("sourceLang", "it", "targetLang", "en", "level", "BEGINNER",
                        "theme", "food", "kind", "WORD", "count", 101)),
                json.writeValueAsString(Map.of("sourceLang", "it", "targetLang", "en", "level", "BEGINNER",
                        "theme", "food", "kind", "WORD", "count", 0))}) {
            mvc.perform(post("/api/v1/platform/generation-runs").header("Authorization", admin)
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void shouldRecordRejectionsForItemsTooHardForTheLevel() throws Exception {
        String admin = signIn("admin", true);
        // Fake phrases of five or more words score above the beginner band; the rest pass.
        mvc.perform(post("/api/v1/platform/generation-runs").header("Authorization", admin)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("sourceLang", "it", "targetLang", "en", "level", "BEGINNER",
                                "theme", "work", "kind", "PHRASE", "count", 3))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.generatedCount").value(greaterThan(0)));
        Integer beyond = jdbc.queryForObject("select count(*) from content_item where difficulty > 33", Integer.class);
        assertEquals(0, beyond);
    }
}
