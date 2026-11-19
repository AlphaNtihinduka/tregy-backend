package com.tregy.backend.repository;

import com.tregy.backend.model.LearnerTheme;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearnerThemeRepository extends JpaRepository<LearnerTheme, String> {

    List<LearnerTheme> findByUserIdOrderByThemeCodeAsc(String userId);

    void deleteByUserId(String userId);
}
