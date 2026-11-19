package com.tregy.backend.integrationtests.controller;

import com.tregy.backend.integrationtests.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProfileIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void clean() {
        jdbc.update("delete from learner_theme");
        jdbc.update("delete from learner_profile");
        jdbc.update("delete from refresh_token");
        jdbc.update("delete from device");
        jdbc.update("delete from app_user");
    }

    private static final java.util.concurrent.atomic.AtomicInteger COUNTER = new java.util.concurrent.atomic.AtomicInteger();

    // A unique address per sign-up: the per-email register limit (5 per 10 minutes) is real
    // behaviour and must not be tripped by tests that reuse a name.
    private String signIn(String name) throws Exception {
        String email = name.replace("@example.com", "") + COUNTER.incrementAndGet() + "@example.com";
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD,
                                "uiLanguage", "it", "installationId", "install-" + email))))
                .andExpect(status().isCreated());
        String body = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD,
                                "installationId", "install-" + email))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("accessToken").asString();
    }

    private String profile(String source, String target, String level) throws Exception {
        return json.writeValueAsString(Map.of("sourceLang", source, "targetLang", target, "level", level));
    }

    @Test
    void shouldRejectProfileAndThemeCallsWhenUnauthenticated() throws Exception {
        mvc.perform(get("/api/v1/me/profile")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/themes")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/me/themes").contentType("application/json").content("{\"themes\":[\"travel\"]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn404WhenProfileNotCreatedYet() throws Exception {
        String auth = signIn("a@example.com");
        mvc.perform(get("/api/v1/me/profile").header("Authorization", auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"));
    }

    @Test
    void shouldCreateProfileWithDefaultsAndThenUpdateIt() throws Exception {
        String auth = signIn("a@example.com");
        mvc.perform(put("/api/v1/me/profile").header("Authorization", auth).contentType("application/json")
                        .content(profile("it", "en", "BEGINNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceLang").value("it"))
                .andExpect(jsonPath("$.targetLang").value("en"))
                .andExpect(jsonPath("$.level").value("BEGINNER"))
                .andExpect(jsonPath("$.gateSize").value(3))
                .andExpect(jsonPath("$.unlockMinutes").value(15))
                .andExpect(jsonPath("$.creepMode").value("GENTLE"));

        String update = json.writeValueAsString(Map.of("sourceLang", "en", "targetLang", "it", "level", "ADVANCED",
                "gateSize", 2, "unlockMinutes", 30, "creepMode", "OFF"));
        mvc.perform(put("/api/v1/me/profile").header("Authorization", auth).contentType("application/json")
                        .content(update))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/profile").header("Authorization", auth))
                .andExpect(jsonPath("$.sourceLang").value("en"))
                .andExpect(jsonPath("$.level").value("ADVANCED"))
                .andExpect(jsonPath("$.gateSize").value(2))
                .andExpect(jsonPath("$.creepMode").value("OFF"));
    }

    @Test
    void shouldRejectInvalidProfiles() throws Exception {
        String auth = signIn("a@example.com");
        // Same language on both sides.
        mvc.perform(put("/api/v1/me/profile").header("Authorization", auth).contentType("application/json")
                        .content(profile("en", "en", "BEGINNER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DIRECTION"));
        // Unsupported language.
        mvc.perform(put("/api/v1/me/profile").header("Authorization", auth).contentType("application/json")
                        .content(profile("fr", "en", "BEGINNER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        // Gate size beyond the cap, unlock window out of range, unknown level.
        for (Map<String, Object> bad : List.<Map<String, Object>>of(
                Map.of("sourceLang", "it", "targetLang", "en", "level", "BEGINNER", "gateSize", 6),
                Map.of("sourceLang", "it", "targetLang", "en", "level", "BEGINNER", "unlockMinutes", 61),
                Map.of("sourceLang", "it", "targetLang", "en", "level", "BEGINNER", "creepStep", 4),
                Map.of("sourceLang", "it", "targetLang", "en", "level", "EXPERT"))) {
            mvc.perform(put("/api/v1/me/profile").header("Authorization", auth).contentType("application/json")
                            .content(json.writeValueAsString(bad)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void shouldResetCreepStepWhenCreepIsSwitchedOff() throws Exception {
        String auth = signIn("a@example.com");
        mvc.perform(put("/api/v1/me/profile").header("Authorization", auth).contentType("application/json")
                .content(json.writeValueAsString(Map.of("sourceLang", "it", "targetLang", "en", "level", "BEGINNER",
                        "creepStep", 2)))).andExpect(jsonPath("$.creepStep").value(2));
        mvc.perform(put("/api/v1/me/profile").header("Authorization", auth).contentType("application/json")
                .content(json.writeValueAsString(Map.of("sourceLang", "it", "targetLang", "en", "level", "BEGINNER",
                        "creepMode", "OFF")))).andExpect(jsonPath("$.creepStep").value(0));
    }

    @Test
    void shouldNotLetOneUserSeeOrChangeAnotherUsersProfile() throws Exception {
        String alice = signIn("alice@example.com");
        String bob = signIn("bob@example.com");
        mvc.perform(put("/api/v1/me/profile").header("Authorization", alice).contentType("application/json")
                .content(profile("it", "en", "ADVANCED"))).andExpect(status().isOk());

        // Bob has no profile of his own: he gets 404, never Alice's data.
        mvc.perform(get("/api/v1/me/profile").header("Authorization", bob))
                .andExpect(status().isNotFound());

        mvc.perform(put("/api/v1/me/profile").header("Authorization", bob).contentType("application/json")
                .content(profile("en", "it", "BEGINNER"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/profile").header("Authorization", alice))
                .andExpect(jsonPath("$.sourceLang").value("it"))
                .andExpect(jsonPath("$.level").value("ADVANCED"));
    }

    @Test
    void shouldListSelectableThemesWithoutGeneral() throws Exception {
        String auth = signIn("a@example.com");
        mvc.perform(get("/api/v1/themes").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", contains("travel", "work", "dating", "food")))
                .andExpect(jsonPath("$[0].nameIt").value("Viaggi"));
    }

    @Test
    void shouldReplaceThemesAndKeepThemPerUser() throws Exception {
        String alice = signIn("alice@example.com");
        String bob = signIn("bob@example.com");
        mvc.perform(put("/api/v1/me/themes").header("Authorization", alice).contentType("application/json")
                        .content("{\"themes\":[\"travel\",\"food\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", contains("food", "travel")));
        // Replacing with an overlapping set must not trip the unique constraint.
        mvc.perform(put("/api/v1/me/themes").header("Authorization", alice).contentType("application/json")
                        .content("{\"themes\":[\"food\",\"work\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", contains("food", "work")));
        mvc.perform(get("/api/v1/me/themes").header("Authorization", alice))
                .andExpect(jsonPath("$", contains("food", "work")));
        mvc.perform(get("/api/v1/me/themes").header("Authorization", bob))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void shouldRejectUnknownEmptyAndGeneralThemes() throws Exception {
        String auth = signIn("a@example.com");
        for (String body : List.of("{\"themes\":[\"nope\"]}", "{\"themes\":[\"general\"]}")) {
            mvc.perform(put("/api/v1/me/themes").header("Authorization", auth).contentType("application/json")
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("UNKNOWN_THEME"));
        }
        mvc.perform(put("/api/v1/me/themes").header("Authorization", auth).contentType("application/json")
                        .content("{\"themes\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
