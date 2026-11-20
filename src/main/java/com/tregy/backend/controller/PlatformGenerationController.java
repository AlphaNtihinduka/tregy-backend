package com.tregy.backend.controller;

import com.tregy.backend.dto.request.CreateGenerationRunRequestDTO;
import com.tregy.backend.dto.response.GenerationRunResponseDTO;
import com.tregy.backend.service.ContentGenerationService;
import com.tregy.backend.service.support.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Operator endpoints. SecurityConfig restricts /api/v1/platform/** to PLATFORM_ADMIN (403 for others). */
@RestController
@RequestMapping("/api/v1/platform")
public class PlatformGenerationController {

    private final ContentGenerationService service;

    public PlatformGenerationController(ContentGenerationService service) {
        this.service = service;
    }

    @PostMapping("/generation-runs")
    @ResponseStatus(HttpStatus.CREATED)
    GenerationRunResponseDTO create(@AuthenticationPrincipal AppUserPrincipal principal,
                                    @Valid @RequestBody CreateGenerationRunRequestDTO request) {
        return service.run(principal.userId(), request);
    }

    @GetMapping("/generation-runs")
    List<GenerationRunResponseDTO> list(@RequestParam(defaultValue = "20") int limit) {
        return service.recentRuns(limit);
    }
}
