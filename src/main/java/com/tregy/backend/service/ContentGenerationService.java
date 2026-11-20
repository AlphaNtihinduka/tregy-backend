package com.tregy.backend.service;

import com.tregy.backend.dto.request.CreateGenerationRunRequestDTO;
import com.tregy.backend.dto.response.GenerationRunResponseDTO;

import java.util.List;

public interface ContentGenerationService {

    /** Generates, validates and stores items for one bucket. Re-running tops up and never duplicates. */
    GenerationRunResponseDTO run(String adminUserId, CreateGenerationRunRequestDTO request);

    List<GenerationRunResponseDTO> recentRuns(int limit);
}
