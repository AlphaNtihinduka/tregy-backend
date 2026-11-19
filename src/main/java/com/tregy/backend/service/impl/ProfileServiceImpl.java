package com.tregy.backend.service.impl;

import com.tregy.backend.dto.request.UpdateProfileRequestDTO;
import com.tregy.backend.dto.request.UpdateThemesRequestDTO;
import com.tregy.backend.dto.response.ProfileResponseDTO;
import com.tregy.backend.dto.response.ThemeResponseDTO;
import com.tregy.backend.exception.AuthException;
import com.tregy.backend.model.LearnerProfile;
import com.tregy.backend.model.LearnerTheme;
import com.tregy.backend.model.Theme;
import com.tregy.backend.model.enums.CreepMode;
import com.tregy.backend.repository.LearnerProfileRepository;
import com.tregy.backend.repository.LearnerThemeRepository;
import com.tregy.backend.repository.ThemeRepository;
import com.tregy.backend.service.ProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProfileServiceImpl implements ProfileService {

    private final LearnerProfileRepository profiles;
    private final LearnerThemeRepository learnerThemes;
    private final ThemeRepository themes;
    private final Clock clock;

    public ProfileServiceImpl(LearnerProfileRepository profiles, LearnerThemeRepository learnerThemes,
                              ThemeRepository themes, Clock clock) {
        this.profiles = profiles;
        this.learnerThemes = learnerThemes;
        this.themes = themes;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileResponseDTO get(String userId) {
        // The profile id is the user id, so this lookup can only ever return the caller's row.
        return profiles.findById(userId)
                .map(ProfileServiceImpl::toResponse)
                .orElseThrow(() -> new AuthException("PROFILE_NOT_FOUND", "No learning profile yet"));
    }

    @Override
    @Transactional
    public ProfileResponseDTO upsert(String userId, UpdateProfileRequestDTO r) {
        if (r.sourceLang().equals(r.targetLang())) {
            throw new AuthException("INVALID_DIRECTION", "Source and target language must differ");
        }
        Instant now = Instant.now(clock);
        LearnerProfile p = profiles.findById(userId).orElseGet(() -> {
            LearnerProfile created = new LearnerProfile();
            created.setUserId(userId);
            created.setCreatedAt(now);
            return created;
        });
        p.setSourceLang(r.sourceLang());
        p.setTargetLang(r.targetLang());
        p.setLevel(r.level());
        if (r.gateSize() != null) p.setGateSize(r.gateSize());
        if (r.unlockMinutes() != null) p.setUnlockMinutes(r.unlockMinutes());
        if (r.creepMode() != null) p.setCreepMode(r.creepMode());
        // Creep step is meaningless with creep off, so switching it off resets the step.
        if (r.creepStep() != null) p.setCreepStep(r.creepStep());
        if (p.getCreepMode() == CreepMode.OFF) p.setCreepStep(0);
        if (r.successfulUnlocks() != null) p.setSuccessfulUnlocks(r.successfulUnlocks());
        p.setUpdatedAt(now);
        return toResponse(profiles.save(p));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ThemeResponseDTO> listThemes() {
        return themes.findBySelectableTrueOrderBySortOrderAsc().stream()
                .map(t -> new ThemeResponseDTO(t.getCode(), t.getNameEn(), t.getNameIt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getThemes(String userId) {
        return learnerThemes.findByUserIdOrderByThemeCodeAsc(userId).stream()
                .map(LearnerTheme::getThemeCode)
                .toList();
    }

    @Override
    @Transactional
    public List<String> setThemes(String userId, UpdateThemesRequestDTO request) {
        Set<String> wanted = new LinkedHashSet<>(request.themes());
        Set<String> known = themes.findByCodeInAndSelectableTrue(wanted).stream()
                .map(Theme::getCode)
                .collect(Collectors.toSet());
        if (!known.containsAll(wanted)) {
            throw new AuthException("UNKNOWN_THEME", "One or more themes do not exist");
        }
        // Diff instead of delete-all-then-insert: Hibernate flushes inserts before deletes, so
        // re-adding a theme in the same transaction would hit the unique constraint.
        List<LearnerTheme> existing = learnerThemes.findByUserIdOrderByThemeCodeAsc(userId);
        Instant now = Instant.now(clock);
        learnerThemes.deleteAll(existing.stream().filter(t -> !wanted.contains(t.getThemeCode())).toList());
        Set<String> kept = existing.stream().map(LearnerTheme::getThemeCode).collect(Collectors.toSet());
        learnerThemes.saveAll(wanted.stream()
                .filter(code -> !kept.contains(code))
                .map(code -> new LearnerTheme(userId, code, now))
                .toList());
        return wanted.stream().sorted().toList();
    }

    private static ProfileResponseDTO toResponse(LearnerProfile p) {
        return new ProfileResponseDTO(p.getSourceLang(), p.getTargetLang(), p.getLevel(), p.getGateSize(),
                p.getUnlockMinutes(), p.getCreepMode(), p.getCreepStep(), p.getSuccessfulUnlocks(), p.getUpdatedAt());
    }
}
