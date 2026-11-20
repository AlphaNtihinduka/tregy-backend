package com.tregy.backend.dto.request;

import com.tregy.backend.model.enums.ContentKind;
import com.tregy.backend.model.enums.LearningLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateGenerationRunRequestDTO(
        @NotBlank @Pattern(regexp = "en|it") String sourceLang,
        @NotBlank @Pattern(regexp = "en|it") String targetLang,
        @NotNull LearningLevel level,
        @NotBlank String theme,
        @NotNull ContentKind kind,
        @Min(1) @Max(500) int count) {
}
