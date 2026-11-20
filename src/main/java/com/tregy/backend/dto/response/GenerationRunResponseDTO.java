package com.tregy.backend.dto.response;

import java.time.Instant;
import java.util.Map;

public record GenerationRunResponseDTO(String id, String sourceLang, String targetLang, String level, String theme,
                                       String kind, int requestedCount, int generatedCount, int acceptedCount,
                                       int rejectedCount, Map<String, Integer> rejections, String status,
                                       String promptVersion, String modelId, long costMicroUsd, Instant createdAt,
                                       Instant finishedAt) {
}
