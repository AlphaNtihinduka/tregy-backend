package com.tregy.backend.service.impl;

import com.tregy.backend.dto.request.PublishBucketRequestDTO;
import com.tregy.backend.dto.response.ContentItemResponseDTO;
import com.tregy.backend.dto.response.PublishResponseDTO;
import com.tregy.backend.exception.AuthException;
import com.tregy.backend.model.ContentItem;
import com.tregy.backend.model.enums.ContentStatus;
import com.tregy.backend.repository.ContentItemRepository;
import com.tregy.backend.repository.ThemeRepository;
import com.tregy.backend.service.ContentPublishingService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class ContentPublishingServiceImpl implements ContentPublishingService {

    private final ContentItemRepository items;
    private final ThemeRepository themes;
    private final Clock clock;

    public ContentPublishingServiceImpl(ContentItemRepository items, ThemeRepository themes, Clock clock) {
        this.items = items;
        this.themes = themes;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PublishResponseDTO publish(PublishBucketRequestDTO r) {
        if (!themes.existsById(r.theme())) {
            throw new AuthException("UNKNOWN_THEME", "Unknown theme");
        }
        return new PublishResponseDTO(
                items.publishBucket(r.sourceLang(), r.targetLang(), r.level(), r.theme(), clock.instant()));
    }

    @Override
    @Transactional
    public ContentItemResponseDTO withdraw(String itemId) {
        ContentItem item = items.findById(itemId).orElseThrow(() -> new AuthException("NOT_FOUND", "No such item"));
        // Only the status and the withdrawn time ever change on an item; its text and difficulty never do.
        if (item.getStatus() != ContentStatus.WITHDRAWN) {
            item.setStatus(ContentStatus.WITHDRAWN);
            item.setWithdrawnAt(clock.instant());
        }
        return toResponse(item);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContentItemResponseDTO> reviewQueue(int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 200));
        return items.findByStatusOrderByCreatedAtDesc(ContentStatus.VALIDATED, PageRequest.of(Math.max(page, 0), safeSize))
                .map(ContentPublishingServiceImpl::toResponse).getContent();
    }

    private static ContentItemResponseDTO toResponse(ContentItem i) {
        return new ContentItemResponseDTO(i.getId(), i.getSourceLang(), i.getTargetLang(), i.getLevel().name(),
                i.getTheme(), i.getKind().name(), i.getPos(), i.getSourceText(), i.getTargetText(), i.getDifficulty(),
                i.getStatus().name(), i.getGenerationRunId(), i.getCreatedAt());
    }
}
