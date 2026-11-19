package com.tregy.backend.model;

import com.tregy.backend.model.enums.CreepMode;
import com.tregy.backend.model.enums.LearningLevel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "learner_profile")
@Getter
@Setter
@NoArgsConstructor
public class LearnerProfile {

    @Id
    @Column(name = "user_id", length = 36, updatable = false)
    private String userId;

    @Column(name = "source_lang", nullable = false, length = 5)
    private String sourceLang;

    @Column(name = "target_lang", nullable = false, length = 5)
    private String targetLang;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LearningLevel level;

    @Column(name = "gate_size", nullable = false)
    private int gateSize = 3;

    @Column(name = "unlock_minutes", nullable = false)
    private int unlockMinutes = 15;

    @Enumerated(EnumType.STRING)
    @Column(name = "creep_mode", nullable = false, length = 20)
    private CreepMode creepMode = CreepMode.GENTLE;

    @Column(name = "creep_step", nullable = false)
    private int creepStep;

    @Column(name = "successful_unlocks", nullable = false)
    private int successfulUnlocks;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
