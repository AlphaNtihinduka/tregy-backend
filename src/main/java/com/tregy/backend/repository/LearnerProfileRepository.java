package com.tregy.backend.repository;

import com.tregy.backend.model.LearnerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

// The id is the user id, so findById(userId) is already scoped to the caller's own row.
public interface LearnerProfileRepository extends JpaRepository<LearnerProfile, String> {
}
