package com.tregy.backend.controller;

import com.tregy.backend.dto.response.PackManifestEntryDTO;
import com.tregy.backend.dto.response.PackResponseDTO;
import com.tregy.backend.service.ContentPackService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Pack download for signed-in users. Packs hold published content only and no user data. */
@RestController
@RequestMapping("/api/v1/content")
public class ContentPackController {

    private final ContentPackService service;

    public ContentPackController(ContentPackService service) {
        this.service = service;
    }

    @GetMapping("/packs")
    List<PackManifestEntryDTO> manifest(@RequestParam String sourceLang, @RequestParam String targetLang,
                                        @RequestParam List<String> levels,
                                        @RequestParam(defaultValue = "") List<String> themes) {
        return service.manifest(sourceLang, targetLang, levels, themes);
    }

    @GetMapping("/packs/{packId}")
    PackResponseDTO pack(@PathVariable String packId) {
        return service.pack(packId);
    }
}
