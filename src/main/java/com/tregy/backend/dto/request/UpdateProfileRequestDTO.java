package com.tregy.backend.dto.request;

import com.tregy.backend.model.enums.CreepMode;
import com.tregy.backend.model.enums.LearningLevel;
import jakarta.validation.constraints.*;

// creepStep and successfulUnlocks are device-owned counters; null leaves the stored value.
// The (sourceLang, targetLang) pair is also checked in the service and by a database constraint.
public record UpdateProfileRequestDTO(
        @NotBlank @Pattern(regexp = "en|it") String sourceLang,
        @NotBlank @Pattern(regexp = "en|it") String targetLang,
        @NotNull LearningLevel level,
        @Min(1) @Max(5) Integer gateSize,
        @Min(5) @Max(60) Integer unlockMinutes,
        CreepMode creepMode,
        @Min(0) @Max(3) Integer creepStep,
        @Min(0) @Max(1_000_000) Integer successfulUnlocks) {
}
