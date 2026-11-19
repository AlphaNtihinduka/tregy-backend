package com.tregy.backend.repository;

import com.tregy.backend.model.Theme;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ThemeRepository extends JpaRepository<Theme, String> {

    List<Theme> findBySelectableTrueOrderBySortOrderAsc();

    List<Theme> findByCodeInAndSelectableTrue(Collection<String> codes);
}
