package com.tregy.backend.controller;

import com.tregy.backend.dto.request.UpdateProfileRequestDTO;
import com.tregy.backend.dto.request.UpdateThemesRequestDTO;
import com.tregy.backend.dto.response.ProfileResponseDTO;
import com.tregy.backend.dto.response.ThemeResponseDTO;
import com.tregy.backend.service.ProfileService;
import com.tregy.backend.service.support.AppUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ProfileController {

    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping("/me/profile")
    ProfileResponseDTO profile(@AuthenticationPrincipal AppUserPrincipal principal) {
        return service.get(principal.userId());
    }

    @PutMapping("/me/profile")
    ProfileResponseDTO updateProfile(@AuthenticationPrincipal AppUserPrincipal principal,
                                     @Valid @RequestBody UpdateProfileRequestDTO request) {
        return service.upsert(principal.userId(), request);
    }

    @GetMapping("/themes")
    List<ThemeResponseDTO> themes() {
        return service.listThemes();
    }

    @GetMapping("/me/themes")
    List<String> myThemes(@AuthenticationPrincipal AppUserPrincipal principal) {
        return service.getThemes(principal.userId());
    }

    @PutMapping("/me/themes")
    List<String> updateThemes(@AuthenticationPrincipal AppUserPrincipal principal,
                              @Valid @RequestBody UpdateThemesRequestDTO request) {
        return service.setThemes(principal.userId(), request);
    }
}
