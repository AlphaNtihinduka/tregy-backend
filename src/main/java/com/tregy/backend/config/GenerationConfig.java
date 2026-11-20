package com.tregy.backend.config;

import com.tregy.backend.service.support.content.FakeLlmClient;
import com.tregy.backend.service.support.content.LlmClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the model client from config. Only the fake exists for now; a real Claude client will be
 * added behind the same interface, with the model id and key read from the environment. An unknown
 * provider fails at startup rather than silently falling back.
 */
@Configuration
public class GenerationConfig {

    @Bean
    LlmClient llmClient(@Value("${app.generation.provider:fake}") String provider,
                        @Value("${app.generation.model-id:fake-model}") String modelId) {
        if ("fake".equals(provider)) {
            return new FakeLlmClient(modelId);
        }
        throw new IllegalStateException("Unknown app.generation.provider '" + provider
                + "'. Only 'fake' is implemented; no model is called.");
    }
}
