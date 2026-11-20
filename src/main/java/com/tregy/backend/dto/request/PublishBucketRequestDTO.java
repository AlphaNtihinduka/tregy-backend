package com.tregy.backend.dto.request;

import com.tregy.backend.model.enums.LearningLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record PublishBucketRequestDTO(
        @NotBlank @Pattern(regexp = "en|it") String sourceLang,
        @NotBlank @Pattern(regexp = "en|it") String targetLang,
        @NotNull LearningLevel level,
        @NotBlank String theme) {
}
