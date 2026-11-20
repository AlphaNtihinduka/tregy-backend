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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ContentPackIntegrationTest extends BaseIntegrationTest {

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

    private void generate(String admin, String level, String theme, String kind) throws Exception {
        mvc.perform(post("/api/v1/platform/generation-runs").header("Authorization", admin)
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("sourceLang", "it", "targetLang", "en", "level", level,
                        "theme", theme, "kind", kind, "count", 50)))).andExpect(status().isCreated());
    }

    private String publishBody(String level, String theme) {
        return json.writeValueAsString(Map.of("sourceLang", "it", "targetLang", "en", "level", level, "theme", theme));
    }

    private static final String MANIFEST = "/api/v1/content/packs?sourceLang=it&targetLang=en&levels=BEGINNER,INTERMEDIATE&themes=food";

    @Test
    void shouldKeepDraftsOutOfPacksUntilPublished() throws Exception {
        String admin = signIn("admin", true);
        String user = signIn("user", false);
        generate(admin, "INTERMEDIATE", "food", "WORD");
        mvc.perform(get(MANIFEST).header("Authorization", user)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/v1/platform/content-items/review").header("Authorization", admin))
                .andExpect(jsonPath("$", hasSize(8))).andExpect(jsonPath("$[0].status").value("VALIDATED"));

        mvc.perform(post("/api/v1/platform/content/publish").header("Authorization", admin)
                        .contentType("application/json").content(publishBody("INTERMEDIATE", "food")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.published").value(8));
        mvc.perform(post("/api/v1/platform/content/publish").header("Authorization", admin)
                        .contentType("application/json").content(publishBody("INTERMEDIATE", "food")))
                .andExpect(jsonPath("$.published").value(0));

        mvc.perform(get(MANIFEST).header("Authorization", user))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].packId").value("it-en-INTERMEDIATE-food"))
                .andExpect(jsonPath("$[0].itemCount").value(8));
    }

    @Test
    void shouldServeAPackInThePhonesShapeWithNoUserData() throws Exception {
        String admin = signIn("admin", true);
        String user = signIn("user", false);
        generate(admin, "INTERMEDIATE", "food", "WORD");
        mvc.perform(post("/api/v1/platform/content/publish").header("Authorization", admin)
                .contentType("application/json").content(publishBody("INTERMEDIATE", "food")));
        mvc.perform(get("/api/v1/content/packs/it-en-INTERMEDIATE-food").header("Authorization", user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("food"))
                .andExpect(jsonPath("$.languages", contains("it", "en")))
                .andExpect(jsonPath("$.entries", hasSize(8)))
                .andExpect(jsonPath("$.entries[0].text.it").isNotEmpty())
                .andExpect(jsonPath("$.entries[0].text.en").isNotEmpty())
                .andExpect(jsonPath("$.entries[0].accepted.en[0]").isNotEmpty())
                .andExpect(jsonPath("$.entries[0].difficulty").isNumber());
    }

    @Test
    void shouldIncludeTheGeneralPackAndAllRequestedLevels() throws Exception {
        String admin = signIn("admin", true);
        String user = signIn("user", false);
        generate(admin, "BEGINNER", "general", "WORD");
        generate(admin, "INTERMEDIATE", "food", "WORD");
        for (String[] bucket : new String[][]{{"BEGINNER", "general"}, {"INTERMEDIATE", "food"}}) {
            mvc.perform(post("/api/v1/platform/content/publish").header("Authorization", admin)
                    .contentType("application/json").content(publishBody(bucket[0], bucket[1]))).andExpect(status().isOk());
        }
        mvc.perform(get(MANIFEST).header("Authorization", user))
                .andExpect(jsonPath("$[*].packId", containsInAnyOrder("it-en-BEGINNER-general", "it-en-INTERMEDIATE-food")));
        // A level that was not asked for is not offered.
        mvc.perform(get("/api/v1/content/packs?sourceLang=it&targetLang=en&levels=BEGINNER&themes=food")
                        .header("Authorization", user))
                .andExpect(jsonPath("$[*].packId", contains("it-en-BEGINNER-general")));
    }

    @Test
    void shouldRaiseTheVersionWhenAnItemIsWithdrawnAndDropItFromThePack() throws Exception {
        String admin = signIn("admin", true);
        String user = signIn("user", false);
        generate(admin, "INTERMEDIATE", "food", "WORD");
        mvc.perform(post("/api/v1/platform/content/publish").header("Authorization", admin)
                .contentType("application/json").content(publishBody("INTERMEDIATE", "food")));
        String before = mvc.perform(get(MANIFEST).header("Authorization", user)).andReturn().getResponse().getContentAsString();
        long versionBefore = json.readTree(before).get(0).get("version").asLong();
        String id = jdbc.queryForObject("select id from content_item limit 1", String.class);
        String text = jdbc.queryForObject("select target_text from content_item where id = ?", String.class, id);

        Thread.sleep(5);
        mvc.perform(post("/api/v1/platform/content-items/" + id + "/withdraw").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WITHDRAWN"));

        String after = mvc.perform(get(MANIFEST).header("Authorization", user)).andReturn().getResponse().getContentAsString();
        assertTrue(json.readTree(after).get(0).get("version").asLong() > versionBefore);
        assertEquals(7, json.readTree(after).get(0).get("itemCount").asInt());
        // The withdrawn item's text is untouched: only its status changed.
        assertEquals(text, jdbc.queryForObject("select target_text from content_item where id = ?", String.class, id));
        mvc.perform(get("/api/v1/content/packs/it-en-INTERMEDIATE-food").header("Authorization", user))
                .andExpect(jsonPath("$.entries", hasSize(7)));
    }

    @Test
    void shouldRefuseUsersOnOperatorEndpointsAndRejectBadPackRequests() throws Exception {
        String user = signIn("user", false);
        mvc.perform(post("/api/v1/platform/content/publish").header("Authorization", user)
                .contentType("application/json").content(publishBody("BEGINNER", "food"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/platform/content-items/x/withdraw").header("Authorization", user))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/content/packs?sourceLang=it&targetLang=en&levels=BEGINNER"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/content/packs/it-en-BEGINNER-food").header("Authorization", user))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/content/packs/garbage").header("Authorization", user)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/content/packs?sourceLang=it&targetLang=it&levels=BEGINNER").header("Authorization", user))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/content/packs?sourceLang=it&targetLang=en&levels=EXPERT").header("Authorization", user))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn404WhenWithdrawingAnUnknownItem() throws Exception {
        String admin = signIn("admin", true);
        mvc.perform(post("/api/v1/platform/content-items/nope/withdraw").header("Authorization", admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldExportASampleAsCsvForReviewAndKeepAccents() throws Exception {
        String admin = signIn("admin", true);
        String user = signIn("user", false);
        generate(admin, "INTERMEDIATE", "food", "WORD");
        String csv = mvc.perform(get("/api/v1/platform/content-items/export?theme=food&limit=3")
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");
        assertEquals(4, lines.length);
        assertTrue(lines[0].startsWith("id,source_lang,target_lang,level,theme,kind,difficulty,status,source_text"));
        assertTrue(lines[0].endsWith("verdict,notes"));
        assertTrue(lines[1].endsWith(",,"), "verdict and notes are left blank for the reviewer");
        mvc.perform(get("/api/v1/platform/content-items/export").header("Authorization", user))
                .andExpect(status().isForbidden());
        // A filter that matches nothing is just the header.
        String empty = mvc.perform(get("/api/v1/platform/content-items/export?theme=dating").header("Authorization", admin))
                .andReturn().getResponse().getContentAsString();
        assertEquals(1, empty.split("\r\n").length);
    }
}
