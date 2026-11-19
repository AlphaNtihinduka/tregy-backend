package com.tregy.backend.dto.response;

import com.tregy.backend.model.enums.CreepMode;
import com.tregy.backend.model.enums.LearningLevel;

import java.time.Instant;

public record ProfileResponseDTO(
        String sourceLang,
        String targetLang,
        LearningLevel level,
        int gateSize,
        int unlockMinutes,
        CreepMode creepMode,
        int creepStep,
        int successfulUnlocks,
        Instant updatedAt) {
}
