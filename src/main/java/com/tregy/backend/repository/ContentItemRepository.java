package com.tregy.backend.repository;

import com.tregy.backend.model.ContentItem;
import com.tregy.backend.model.enums.LearningLevel;
import com.tregy.backend.model.enums.ContentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ContentItemRepository extends JpaRepository<ContentItem, String> {

    /** Target texts of live (not withdrawn) items in a direction, for duplicate detection. */
    @Query("select i.dedupeKey from ContentItem i where i.sourceLang = :sourceLang and i.targetLang = :targetLang "
            + "and i.status <> com.tregy.backend.model.enums.ContentStatus.WITHDRAWN")
    List<String> liveDedupeKeys(String sourceLang, String targetLang);

    @Query("select i.targetText from ContentItem i where i.sourceLang = :sourceLang and i.targetLang = :targetLang "
            + "and i.level = :level and i.theme = :theme and i.status <> com.tregy.backend.model.enums.ContentStatus.WITHDRAWN")
    List<String> liveTargetTexts(String sourceLang, String targetLang, LearningLevel level, String theme);

    /** Publishes every validated item in a bucket. Returns how many changed. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ContentItem i set i.status = com.tregy.backend.model.enums.ContentStatus.PUBLISHED, "
            + "i.publishedAt = :now where i.sourceLang = :sourceLang and i.targetLang = :targetLang "
            + "and i.level = :level and i.theme = :theme "
            + "and i.status = com.tregy.backend.model.enums.ContentStatus.VALIDATED")
    int publishBucket(String sourceLang, String targetLang, LearningLevel level, String theme, java.time.Instant now);

    /** Review queue: items by status, newest first. */
    Page<ContentItem> findByStatusOrderByCreatedAtDesc(ContentStatus status, Pageable pageable);

    /** One row per (level, theme): published count and the latest publish and withdraw instants. */
    @Query("select i.level, i.theme, sum(case when i.status = com.tregy.backend.model.enums.ContentStatus.PUBLISHED "
            + "then 1 else 0 end), max(i.publishedAt), max(i.withdrawnAt) from ContentItem i "
            + "where i.sourceLang = :sourceLang and i.targetLang = :targetLang and i.level in :levels "
            + "and i.theme in :themes and i.publishedAt is not null group by i.level, i.theme")
    List<Object[]> packSummaries(String sourceLang, String targetLang, java.util.Collection<LearningLevel> levels,
                                 java.util.Collection<String> themes);

    List<ContentItem> findBySourceLangAndTargetLangAndLevelAndThemeAndStatusOrderByIdAsc(
            String sourceLang, String targetLang, LearningLevel level, String theme, ContentStatus status);

    /** A random sample for native-speaker review. Every filter is optional (null means any). */
    @Query(value = "select * from content_item where (cast(:sourceLang as varchar) is null or source_lang = cast(:sourceLang as varchar)) "
            + "and (cast(:targetLang as varchar) is null or target_lang = cast(:targetLang as varchar)) "
            + "and (cast(:level as varchar) is null or level = cast(:level as varchar)) "
            + "and (cast(:theme as varchar) is null or theme = cast(:theme as varchar)) "
            + "and (cast(:status as varchar) is null or status = cast(:status as varchar)) "
            + "order by random() limit :limit", nativeQuery = true)
    List<ContentItem> randomSample(String sourceLang, String targetLang, String level, String theme, String status,
                                   int limit);
}
