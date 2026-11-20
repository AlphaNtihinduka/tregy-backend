package com.tregy.backend.controller;

import com.tregy.backend.dto.request.SyncEventsRequestDTO;
import com.tregy.backend.dto.response.ProgressResponseDTO;
import com.tregy.backend.dto.response.SyncEventsResponseDTO;
import com.tregy.backend.service.SyncService;
import com.tregy.backend.service.support.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class SyncController {

    private final SyncService service;

    public SyncController(SyncService service) {
        this.service = service;
    }

    @PostMapping("/sync/events")
    SyncEventsResponseDTO events(@AuthenticationPrincipal AppUserPrincipal principal,
                                 @Valid @RequestBody SyncEventsRequestDTO request) {
        return service.apply(principal.userId(), request);
    }

    @GetMapping("/me/progress")
    ProgressResponseDTO progress(@AuthenticationPrincipal AppUserPrincipal principal,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "200") int size) {
        return service.progress(principal.userId(), page, size);
    }
}
