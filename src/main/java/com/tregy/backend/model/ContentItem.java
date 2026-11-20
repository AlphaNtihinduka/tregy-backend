package com.tregy.backend.model;

import com.tregy.backend.model.enums.ContentKind;
import com.tregy.backend.model.enums.ContentStatus;
import com.tregy.backend.model.enums.LearningLevel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One learning item. The text fields and difficulty are write-once: a published item is never
 * edited (device SRS state refers to its id); a correction is a new item and this one is withdrawn.
 */
@Entity
@Table(name = "content_item")
@Getter
@Setter
@NoArgsConstructor
public class ContentItem {

    @Id
    @Column(length = 36, updatable = false)
    private String id;

    @Column(name = "source_lang", nullable = false, updatable = false, length = 5)
    private String sourceLang;

    @Column(name = "target_lang", nullable = false, updatable = false, length = 5)
    private String targetLang;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private LearningLevel level;

    @Column(nullable = false, updatable = false, length = 30)
    private String theme;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private ContentKind kind;

    @Column(nullable = false, updatable = false, length = 10)
    private String pos;

    @Column(name = "source_text", nullable = false, updatable = false, length = 300)
    private String sourceText;

    @Column(name = "target_text", nullable = false, updatable = false, length = 300)
    private String targetText;

    /** JSON array of strings, canonical answer first. */
    @Column(name = "accepted_variants", nullable = false, updatable = false)
    private String acceptedVariants;

    /** JSON array of strings. */
    @Column(nullable = false, updatable = false)
    private String distractors;

    @Column(nullable = false, updatable = false)
    private int difficulty;

    @Column(name = "dedupe_key", nullable = false, updatable = false, length = 300)
    private String dedupeKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ContentStatus status;

    @Column(nullable = false, updatable = false)
    private int version = 1;

    @Column(name = "generation_run_id", nullable = false, updatable = false, length = 36)
    private String generationRunId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;
}
