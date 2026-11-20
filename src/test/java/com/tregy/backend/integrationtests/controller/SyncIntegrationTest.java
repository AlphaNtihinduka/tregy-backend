package com.tregy.backend.integrationtests.controller;

import com.tregy.backend.integrationtests.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SyncIntegrationTest extends BaseIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";
    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void clean() {
        jdbc.update("delete from sync_event");
        jdbc.update("delete from srs_state");
        jdbc.update("delete from learner_theme");
        jdbc.update("delete from learner_profile");
        jdbc.update("delete from refresh_token");
        jdbc.update("delete from device");
        jdbc.update("delete from app_user");
    }

    private String signIn(String name) throws Exception {
        String email = name + COUNTER.incrementAndGet() + "@example.com";
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD,
                        "uiLanguage", "it", "installationId", "install-" + email)))).andExpect(status().isCreated());
        String body = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD,
                                "installationId", "install-" + email))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("accessToken").asString();
    }

    private Map<String, Object> event(String item, int stage, String updatedAt, String learnedAt) {
        Map<String, Object> e = new HashMap<>();
        e.put("clientEventId", UUID.randomUUID().toString());
        e.put("itemId", item);
        e.put("stage", stage);
        e.put("dueOn", "2026-05-11");
        e.put("learnedAt", learnedAt);
        e.put("lastResult", "correct");
        e.put("lastCorrectOn", "2026-05-10");
        e.put("updatedAt", updatedAt);
        return e;
    }

    private String send(String auth, List<Map<String, Object>> events) throws Exception {
        return mvc.perform(post("/api/v1/sync/events").header("Authorization", auth).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("events", events))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void shouldRequireAuthentication() throws Exception {
        mvc.perform(post("/api/v1/sync/events").contentType("application/json").content("{\"events\":[]}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me/progress")).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAcceptAnEventAndReturnItInProgress() throws Exception {
        String auth = signIn("a");
        send(auth, List.of(event("gen-b-001", 2, "2026-05-10T10:00:00Z", "2026-05-10T10:00:00Z")));
        mvc.perform(get("/api/v1/me/progress").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.learned").value(1))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].itemId").value("gen-b-001"))
                .andExpect(jsonPath("$.items[0].stage").value(2))
                .andExpect(jsonPath("$.items[0].dueOn").value("2026-05-11"));
    }

    @Test
    void shouldTreatARetriedBatchAsDuplicateAndChangeNothing() throws Exception {
        String auth = signIn("a");
        Map<String, Object> e = event("gen-b-001", 1, "2026-05-10T10:00:00Z", null);
        String first = send(auth, List.of(e));
        String second = send(auth, List.of(e));
        org.junit.jupiter.api.Assertions.assertTrue(first.contains("ACCEPTED"));
        org.junit.jupiter.api.Assertions.assertTrue(second.contains("DUPLICATE"));
        org.junit.jupiter.api.Assertions.assertEquals(1,
                jdbc.queryForObject("select count(*) from sync_event", Integer.class));
    }

    @Test
    void shouldLetTheLaterUpdateWinRegardlessOfArrivalOrder() throws Exception {
        String auth = signIn("a");
        send(auth, List.of(event("w", 3, "2026-05-12T10:00:00Z", null)));
        // An older event arriving late must not roll the schedule back.
        send(auth, List.of(event("w", 1, "2026-05-10T10:00:00Z", null)));
        mvc.perform(get("/api/v1/me/progress").header("Authorization", auth))
                .andExpect(jsonPath("$.items[0].stage").value(3));
    }

    @Test
    void shouldKeepTheLearnedFlagOneWayAndEarliest() throws Exception {
        String auth = signIn("a");
        send(auth, List.of(event("w", 2, "2026-05-12T10:00:00Z", "2026-05-12T10:00:00Z")));
        // A later lapse (stage 1, no learnedAt) must not clear it; an earlier learnedAt wins.
        send(auth, List.of(event("w", 1, "2026-05-20T10:00:00Z", null)));
        send(auth, List.of(event("w", 1, "2026-05-01T10:00:00Z", "2026-05-11T10:00:00Z")));
        mvc.perform(get("/api/v1/me/progress").header("Authorization", auth))
                .andExpect(jsonPath("$.learned").value(1))
                .andExpect(jsonPath("$.items[0].stage").value(1))
                .andExpect(jsonPath("$.items[0].learnedAt").value("2026-05-11T10:00:00Z"));
    }

    @Test
    void shouldRejectBadEventsAloneAndStillApplyTheGoodOnes() throws Exception {
        String auth = signIn("a");
        Map<String, Object> badStage = event("w1", 9, "2026-05-10T10:00:00Z", null);
        Map<String, Object> badDate = event("w2", 1, "yesterday", null);
        Map<String, Object> badId = event("w3", 1, "2026-05-10T10:00:00Z", null);
        badId.put("clientEventId", "not-a-uuid");
        Map<String, Object> badItem = event("has spaces!", 1, "2026-05-10T10:00:00Z", null);
        Map<String, Object> good = event("w4", 1, "2026-05-10T10:00:00Z", null);
        mvc.perform(post("/api/v1/sync/events").header("Authorization", auth).contentType("application/json")
                        .content(json.writeValueAsString(Map.of("events", List.of(badStage, badDate, badId, badItem, good)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].status",
                        contains("REJECTED", "REJECTED", "REJECTED", "REJECTED", "ACCEPTED")))
                .andExpect(jsonPath("$.results[2].code").value("INVALID_EVENT_ID"));
        mvc.perform(get("/api/v1/me/progress").header("Authorization", auth)).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void shouldRejectEmptyAndOversizedBatches() throws Exception {
        String auth = signIn("a");
        mvc.perform(post("/api/v1/sync/events").header("Authorization", auth).contentType("application/json")
                .content("{\"events\":[]}")).andExpect(status().isBadRequest());
        List<Map<String, Object>> many = new java.util.ArrayList<>();
        for (int i = 0; i < 101; i++) many.add(event("w" + i, 1, "2026-05-10T10:00:00Z", null));
        mvc.perform(post("/api/v1/sync/events").header("Authorization", auth).contentType("application/json")
                .content(json.writeValueAsString(Map.of("events", many)))).andExpect(status().isBadRequest());
    }

    @Test
    void shouldKeepEachUsersProgressSeparate() throws Exception {
        String alice = signIn("alice");
        String bob = signIn("bob");
        send(alice, List.of(event("w", 2, "2026-05-10T10:00:00Z", "2026-05-10T10:00:00Z")));
        mvc.perform(get("/api/v1/me/progress").header("Authorization", bob))
                .andExpect(jsonPath("$.learned").value(0)).andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items", hasSize(0)));
        // The same item id and the same event id from another user is that user's own record.
        Map<String, Object> e = event("w", 1, "2026-05-10T10:00:00Z", null);
        send(alice, List.of(e));
        String again = send(bob, List.of(e));
        org.junit.jupiter.api.Assertions.assertTrue(again.contains("ACCEPTED"));
        mvc.perform(get("/api/v1/me/progress").header("Authorization", alice))
                .andExpect(jsonPath("$.items[0].stage").value(2));
    }

    @Test
    void shouldPageProgressAndCapThePageSize() throws Exception {
        String auth = signIn("a");
        List<Map<String, Object>> events = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) events.add(event("item-" + i, 1, "2026-05-10T10:00:00Z", null));
        send(auth, events);
        mvc.perform(get("/api/v1/me/progress?page=1&size=2").header("Authorization", auth))
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].itemId").value("item-2"));
        mvc.perform(get("/api/v1/me/progress?size=100000").header("Authorization", auth))
                .andExpect(jsonPath("$.size").value(500));
    }
}
