package com.tregy.backend.service;

import com.tregy.backend.dto.response.PackManifestEntryDTO;
import com.tregy.backend.dto.response.PackResponseDTO;

import java.util.List;

public interface ContentPackService {

    /** Packs available for a direction, the given levels and themes (the general pack is always included). */
    List<PackManifestEntryDTO> manifest(String sourceLang, String targetLang, List<String> levels, List<String> themes);

    PackResponseDTO pack(String packId);
}
