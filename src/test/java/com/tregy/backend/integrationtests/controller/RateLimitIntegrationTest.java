package com.tregy.backend.integrationtests.controller;

import com.tregy.backend.integrationtests.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// A low limit for this class only, so the 429 path is proven without slowing other tests.
@TestPropertySource(properties = "app.rate-limit.ip-requests=3")
class RateLimitIntegrationTest extends BaseIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void shouldReturn429WithRetryAfterWhenAnAddressExceedsTheLimit() throws Exception {
        String body = "{\"email\":\"nobody@example.com\"}";
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/auth/forgot-password").contentType("application/json").content(body))
                    .andExpect(status().isAccepted());
        }
        mvc.perform(post("/api/v1/auth/forgot-password").contentType("application/json").content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "600"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }
}
