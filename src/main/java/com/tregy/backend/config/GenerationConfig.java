package com.tregy.backend.config;

import com.tregy.backend.service.support.content.FakeLlmClient;
import com.tregy.backend.service.support.content.FileLlmClient;
import com.tregy.backend.service.support.content.LlmClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Chooses the model client from config. {@code fake} (built-in stock, for tests) and {@code file} (content
 * people wrote and checked, in {@code content-seed/} on the classpath) exist; a real Claude client will be
 * added behind the same interface, with the model id and key read from the environment. An unknown
 * provider fails at startup rather than silently falling back.
 */
@Configuration
public class GenerationConfig {

    @Bean
    LlmClient llmClient(@Value("${app.generation.provider:fake}") String provider,
                        @Value("${app.generation.model-id:fake-model}") String modelId,
                        JsonMapper json) {
        if ("fake".equals(provider)) {
            return new FakeLlmClient(modelId);
        }
        if ("file".equals(provider)) {
            return new FileLlmClient(modelId, "content-seed", json);
        }
        throw new IllegalStateException("Unknown app.generation.provider '" + provider
                + "'. Implemented: 'fake', 'file'.");
    }
}
