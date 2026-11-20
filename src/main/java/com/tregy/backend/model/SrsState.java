package com.tregy.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "srs_state")
@IdClass(SrsState.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class SrsState {

    @Id
    @Column(name = "user_id", length = 36, updatable = false)
    private String userId;

    @Id
    @Column(name = "item_id", length = 64, updatable = false)
    private String itemId;

    @Column(nullable = false)
    private int stage;

    @Column(name = "due_on")
    private LocalDate dueOn;

    @Column(name = "learned_at")
    private Instant learnedAt;

    @Column(name = "last_result", length = 10)
    private String lastResult;

    @Column(name = "last_correct_on")
    private LocalDate lastCorrectOn;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public SrsState(String userId, String itemId) {
        this.userId = userId;
        this.itemId = itemId;
    }

    public static class Key implements Serializable {
        private String userId;
        private String itemId;

        public Key() {
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(itemId, k.itemId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, itemId);
        }
    }
}
