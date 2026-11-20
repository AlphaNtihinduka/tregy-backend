package com.tregy.backend.dto.response;

import java.time.Instant;

/** An item as an operator sees it in the review queue. */
public record ContentItemResponseDTO(String id, String sourceLang, String targetLang, String level, String theme,
                                     String kind, String pos, String sourceText, String targetText, int difficulty,
                                     String status, String generationRunId, Instant createdAt) {
}
