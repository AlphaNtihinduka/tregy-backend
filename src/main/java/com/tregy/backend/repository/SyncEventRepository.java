package com.tregy.backend.repository;

import com.tregy.backend.model.SyncEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncEventRepository extends JpaRepository<SyncEvent, SyncEvent.Key> {

    boolean existsByUserIdAndClientEventId(String userId, String clientEventId);
}
