package com.tregy.backend.integrationtests.controller;

import com.tregy.backend.integrationtests.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** A spent budget stops a run cleanly: nothing is generated and the run says why. */
@TestPropertySource(properties = "app.generation.monthly-budget-usd=0")
class GenerationBudgetIntegrationTest extends BaseIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void shouldStopWithoutGeneratingWhenTheMonthlyBudgetIsSpent() throws Exception {
        jdbc.update("delete from content_item");
        jdbc.update("delete from generation_run");
        jdbc.update("delete from refresh_token");
        jdbc.update("delete from device");
        jdbc.update("delete from app_user");
        String email = "budget-admin@example.com";
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", email, "password", "correct-horse-battery",
                        "uiLanguage", "en", "installationId", "install-budget")))).andExpect(status().isCreated());
        jdbc.update("update app_user set platform_role = 'PLATFORM_ADMIN' where email = ?", email);
        String body = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("email", email, "password", "correct-horse-battery",
                                "installationId", "install-budget"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String auth = "Bearer " + json.readTree(body).get("accessToken").asString();

        mvc.perform(post("/api/v1/platform/generation-runs").header("Authorization", auth)
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("sourceLang", "it", "targetLang", "en", "level",
                                "BEGINNER", "theme", "food", "kind", "WORD", "count", 5))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("STOPPED_BUDGET"))
                .andExpect(jsonPath("$.acceptedCount").value(0));
        assertEquals(0, jdbc.queryForObject("select count(*) from content_item", Integer.class));
    }
}
