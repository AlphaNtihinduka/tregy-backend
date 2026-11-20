package com.tregy.backend.service;

import com.tregy.backend.dto.request.PublishBucketRequestDTO;
import com.tregy.backend.dto.response.ContentItemResponseDTO;
import com.tregy.backend.dto.response.PublishResponseDTO;

import java.util.List;

public interface ContentPublishingService {

    /** Publishes every validated item in the bucket. Published items are immutable from then on. */
    PublishResponseDTO publish(PublishBucketRequestDTO request);

    /** Takes an item out of circulation. Its text is never edited; a correction is a new item. */
    ContentItemResponseDTO withdraw(String itemId);

    /** The review queue: items waiting to be published, newest first. */
    List<ContentItemResponseDTO> reviewQueue(int page, int size);

    /** A random sample as CSV for a native speaker. Filters are optional; limit is capped at 200. */
    String reviewExport(String sourceLang, String targetLang, String level, String theme, String status, int limit);
}
