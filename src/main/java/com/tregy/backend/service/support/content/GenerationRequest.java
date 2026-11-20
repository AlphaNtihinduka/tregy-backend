package com.tregy.backend.service.support.content;

import com.tregy.backend.model.enums.ContentKind;
import com.tregy.backend.model.enums.LearningLevel;

import java.util.List;

/**
 * What the generator is asked for. It contains only the direction, level, theme, kind and the
 * target texts to avoid: never any user data (privacy rule, ADR 0003).
 */
public record GenerationRequest(String sourceLang, String targetLang, LearningLevel level, String theme,
                                ContentKind kind, int count, List<String> existingTargets) {
}
