package com.tregy.backend.service.support.content;

import java.util.List;

/**
 * The only door to a language model. Content generation is a server-side batch job; the app never
 * calls this (ADR 0003). Implementations: {@link FakeLlmClient} now, a Claude API client later.
 */
public interface LlmClient {

    /** Identifier recorded on every run (read from config; never hard-coded in logic). */
    String modelId();

    /** Version of the prompt template in use, pinned on each run so a regression can be traced. */
    String promptVersion();

    Result generate(GenerationRequest request);

    /** Items plus token usage, so the run can be costed against the budget. */
    record Result(List<GeneratedItem> items, long inputTokens, long outputTokens) {
    }
}
