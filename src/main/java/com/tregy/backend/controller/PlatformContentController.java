package com.tregy.backend.controller;

import com.tregy.backend.dto.request.PublishBucketRequestDTO;
import com.tregy.backend.dto.response.ContentItemResponseDTO;
import com.tregy.backend.dto.response.PublishResponseDTO;
import com.tregy.backend.service.ContentPublishingService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Operator review and publishing. SecurityConfig restricts /api/v1/platform/** to PLATFORM_ADMIN. */
@RestController
@RequestMapping("/api/v1/platform")
public class PlatformContentController {

    private final ContentPublishingService service;

    public PlatformContentController(ContentPublishingService service) {
        this.service = service;
    }

    @GetMapping("/content-items/review")
    List<ContentItemResponseDTO> reviewQueue(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        return service.reviewQueue(page, size);
    }

    @PostMapping("/content/publish")
    PublishResponseDTO publish(@Valid @RequestBody PublishBucketRequestDTO request) {
        return service.publish(request);
    }

    @PostMapping("/content-items/{id}/withdraw")
    ContentItemResponseDTO withdraw(@PathVariable String id) {
        return service.withdraw(id);
    }

    /** A random sample as CSV for a native speaker to review in a spreadsheet. */
    @GetMapping("/content-items/export")
    ResponseEntity<String> export(@RequestParam(required = false) String sourceLang,
                                  @RequestParam(required = false) String targetLang,
                                  @RequestParam(required = false) String level,
                                  @RequestParam(required = false) String theme,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header("Content-Disposition", "attachment; filename=\"content-review.csv\"")
                .body(service.reviewExport(sourceLang, targetLang, level, theme, status, limit));
    }
}
