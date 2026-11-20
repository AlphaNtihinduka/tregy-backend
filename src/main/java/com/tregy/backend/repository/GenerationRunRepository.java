package com.tregy.backend.repository;

import com.tregy.backend.model.GenerationRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface GenerationRunRepository extends JpaRepository<GenerationRun, String> {

    List<GenerationRun> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Estimated spend since the start of the month, in millionths of a dollar. */
    @Query("select coalesce(sum(r.costMicroUsd), 0) from GenerationRun r where r.createdAt >= :since")
    long spentSince(Instant since);
}
