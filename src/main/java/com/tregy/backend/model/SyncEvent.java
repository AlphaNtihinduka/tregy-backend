package com.tregy.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "sync_event")
@IdClass(SyncEvent.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class SyncEvent {

    @Id
    @Column(name = "user_id", length = 36, updatable = false)
    private String userId;

    @Id
    @Column(name = "client_event_id", length = 36, updatable = false)
    private String clientEventId;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    public SyncEvent(String userId, String clientEventId, Instant receivedAt) {
        this.userId = userId;
        this.clientEventId = clientEventId;
        this.receivedAt = receivedAt;
    }

    public static class Key implements Serializable {
        private String userId;
        private String clientEventId;

        public Key() {
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(clientEventId, k.clientEventId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, clientEventId);
        }
    }
}
