package com.tregy.backend.service;

import com.tregy.backend.dto.request.UpdateProfileRequestDTO;
import com.tregy.backend.dto.request.UpdateThemesRequestDTO;
import com.tregy.backend.dto.response.ProfileResponseDTO;
import com.tregy.backend.dto.response.ThemeResponseDTO;

import java.util.List;

public interface ProfileService {

    /** The caller's own profile; throws PROFILE_NOT_FOUND (404) until onboarding has saved one. */
    ProfileResponseDTO get(String userId);

    /** Creates or updates the caller's profile (onboarding and settings). */
    ProfileResponseDTO upsert(String userId, UpdateProfileRequestDTO request);

    /** Selectable themes (the catalogue), in display order. */
    List<ThemeResponseDTO> listThemes();

    /** Codes of the caller's chosen themes. */
    List<String> getThemes(String userId);

    /** Replaces the caller's chosen themes and returns the new set. */
    List<String> setThemes(String userId, UpdateThemesRequestDTO request);
}
