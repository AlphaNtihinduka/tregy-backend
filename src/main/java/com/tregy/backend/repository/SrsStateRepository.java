package com.tregy.backend.repository;

import com.tregy.backend.model.SrsState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface SrsStateRepository extends JpaRepository<SrsState, SrsState.Key> {

    // Every query carries the user id, so another user's rows can never be returned.
    Optional<SrsState> findByUserIdAndItemId(String userId, String itemId);

    Page<SrsState> findByUserIdOrderByItemIdAsc(String userId, Pageable pageable);

    @Query("select count(s) from SrsState s where s.userId = :userId and s.learnedAt is not null")
    long countLearned(String userId);
}
