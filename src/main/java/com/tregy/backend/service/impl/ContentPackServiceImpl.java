package com.tregy.backend.service.impl;

import com.tregy.backend.dto.response.PackManifestEntryDTO;
import com.tregy.backend.dto.response.PackResponseDTO;
import com.tregy.backend.exception.AuthException;
import com.tregy.backend.model.ContentItem;
import com.tregy.backend.model.enums.ContentStatus;
import com.tregy.backend.model.enums.LearningLevel;
import com.tregy.backend.repository.ContentItemRepository;
import com.tregy.backend.service.ContentPackService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Serves published items as packs: one pack per (direction, level, theme). Packs contain no user
 * data; the phone is the only place a person's progress meets them.
 */
@Service
public class ContentPackServiceImpl implements ContentPackService {

    private static final Set<String> LANGS = Set.of("en", "it");
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };

    private final ContentItemRepository items;
    private final JsonMapper json;

    public ContentPackServiceImpl(ContentItemRepository items, JsonMapper json) {
        this.items = items;
        this.json = json;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PackManifestEntryDTO> manifest(String sourceLang, String targetLang, List<String> levels,
                                               List<String> themes) {
        requireDirection(sourceLang, targetLang);
        List<LearningLevel> parsedLevels = new ArrayList<>();
        for (String level : levels) {
            parsedLevels.add(parseLevel(level));
        }
        if (parsedLevels.isEmpty() || parsedLevels.size() > 3 || themes.size() > 20) {
            throw new AuthException("INVALID_PACK_QUERY", "Ask for 1 to 3 levels and at most 20 themes");
        }
        Set<String> wantedThemes = new LinkedHashSet<>(themes);
        wantedThemes.add("general");
        List<PackManifestEntryDTO> result = new ArrayList<>();
        for (Object[] row : items.packSummaries(sourceLang, targetLang, parsedLevels, wantedThemes)) {
            int count = ((Number) row[2]).intValue();
            if (count == 0) {
                continue;
            }
            LearningLevel level = (LearningLevel) row[0];
            String theme = (String) row[1];
            result.add(new PackManifestEntryDTO(packId(sourceLang, targetLang, level, theme), theme, level.name(),
                    version((Instant) row[3], (Instant) row[4]), count));
        }
        result.sort(java.util.Comparator.comparing(PackManifestEntryDTO::packId));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public PackResponseDTO pack(String packId) {
        // packId is "<source>-<target>-<LEVEL>-<theme>"; anything else is simply not a pack.
        String[] parts = packId.split("-", 4);
        if (parts.length != 4) {
            throw new AuthException("NOT_FOUND", "No such pack");
        }
        requireDirection(parts[0], parts[1]);
        LearningLevel level = parseLevel(parts[2]);
        String theme = parts[3];
        List<ContentItem> published = items.findBySourceLangAndTargetLangAndLevelAndThemeAndStatusOrderByIdAsc(
                parts[0], parts[1], level, theme, ContentStatus.PUBLISHED);
        if (published.isEmpty()) {
            throw new AuthException("NOT_FOUND", "No such pack");
        }
        Instant lastPublished = published.stream().map(ContentItem::getPublishedAt).max(Instant::compareTo).orElse(null);
        // Withdrawals also change the pack, so the version looks at the bucket's latest withdrawal too.
        Instant lastWithdrawn = items.packSummaries(parts[0], parts[1], List.of(level), List.of(theme)).stream()
                .map(r -> (Instant) r[4]).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        List<PackResponseDTO.Entry> entries = published.stream().map(i -> new PackResponseDTO.Entry(
                i.getId(), i.getPos(), i.getDifficulty(),
                Map.of(i.getSourceLang(), i.getSourceText(), i.getTargetLang(), i.getTargetText()),
                Map.of(i.getTargetLang(), json.readValue(i.getAcceptedVariants(), STRINGS)))).toList();
        return new PackResponseDTO(packId, version(lastPublished, lastWithdrawn), theme, level.name(),
                List.of(parts[0], parts[1]), entries);
    }

    private static String packId(String source, String target, LearningLevel level, String theme) {
        return source + "-" + target + "-" + level.name() + "-" + theme;
    }

    /** Milliseconds since the epoch of the latest change to the pack: bigger means newer. */
    private static long version(Instant lastPublished, Instant lastWithdrawn) {
        long published = lastPublished == null ? 0 : lastPublished.toEpochMilli();
        long withdrawn = lastWithdrawn == null ? 0 : lastWithdrawn.toEpochMilli();
        return Math.max(published, withdrawn);
    }

    private static void requireDirection(String source, String target) {
        boolean pair = LANGS.contains(source) && LANGS.contains(target) && !source.equals(target);
        if (!pair) {
            throw new AuthException("INVALID_DIRECTION", "Direction must be it to en or en to it");
        }
    }

    private static LearningLevel parseLevel(String level) {
        try {
            return LearningLevel.valueOf(level);
        } catch (IllegalArgumentException e) {
            throw new AuthException("INVALID_PACK_QUERY", "Unknown level");
        }
    }
}
