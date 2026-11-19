package com.tregy.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "learner_theme")
@Getter
@Setter
@NoArgsConstructor
public class LearnerTheme {

    @Id
    @Column(length = 36, updatable = false)
    private String id;

    @Column(name = "user_id", nullable = false, updatable = false, length = 36)
    private String userId;

    @Column(name = "theme_code", nullable = false, updatable = false, length = 30)
    private String themeCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public LearnerTheme(String userId, String themeCode, Instant createdAt) {
        this.userId = userId;
        this.themeCode = themeCode;
        this.createdAt = createdAt;
    }

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
}
