package com.tregy.backend.repository;

import com.tregy.backend.model.ContentItem;
import com.tregy.backend.model.enums.LearningLevel;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
