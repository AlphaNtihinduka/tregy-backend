package com.tregy.backend.model;

import com.tregy.backend.model.enums.ContentKind;
import com.tregy.backend.model.enums.LearningLevel;
import com.tregy.backend.model.enums.RunStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "generation_run")
@Getter
@Setter
@NoArgsConstructor
public class GenerationRun {

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

    @Column(name = "requested_count", nullable = false, updatable = false)
    private int requestedCount;

    @Column(name = "generated_count", nullable = false)
    private int generatedCount;

    @Column(name = "accepted_count", nullable = false)
    private int acceptedCount;

    @Column(name = "rejected_count", nullable = false)
    private int rejectedCount;

    /** JSON object: rejection reason code to count. */
    @Column(name = "rejection_summary", nullable = false)
    private String rejectionSummary = "{}";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RunStatus status;

    @Column(name = "prompt_version", nullable = false, updatable = false, length = 20)
    private String promptVersion;

    @Column(name = "model_id", nullable = false, updatable = false, length = 80)
    private String modelId;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    @Column(name = "cost_micro_usd", nullable = false)
    private long costMicroUsd;

    @Column(name = "created_by", nullable = false, updatable = false, length = 36)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
