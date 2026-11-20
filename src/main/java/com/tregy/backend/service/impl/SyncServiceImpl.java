package com.tregy.backend.service.impl;

import com.tregy.backend.dto.request.SyncEventsRequestDTO;
import com.tregy.backend.dto.request.SyncEventsRequestDTO.ItemStateEventDTO;
import com.tregy.backend.dto.response.ProgressResponseDTO;
import com.tregy.backend.dto.response.SyncEventsResponseDTO;
import com.tregy.backend.dto.response.SyncEventsResponseDTO.Result;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tregy.backend.exception.AuthException;
import com.tregy.backend.model.SrsState;
import com.tregy.backend.model.SyncEvent;
import com.tregy.backend.repository.SrsStateRepository;
import com.tregy.backend.repository.SyncEventRepository;
import com.tregy.backend.service.SyncService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@Service
public class SyncServiceImpl implements SyncService {

    static final int MAX_PAGE_SIZE = 500;
    private static final Pattern ITEM_ID = Pattern.compile("[A-Za-z0-9_.:-]{1,64}");
    private static final Set<String> RESULTS = Set.of("correct", "wrong");

    private final SrsStateRepository states;
    private final SyncEventRepository events;
    private final Clock clock;
    // Per-user cap on sync calls, so one account cannot hammer the endpoint. Counters live in memory.
    private final Cache<String, AtomicInteger> calls = Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(10_000).build();
    private final int callLimit;

    public SyncServiceImpl(SrsStateRepository states, SyncEventRepository events, Clock clock,
                           @org.springframework.beans.factory.annotation.Value("${app.rate-limit.sync-requests:60}") int callLimit) {
        this.callLimit = callLimit;
        this.states = states;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SyncEventsResponseDTO apply(String userId, SyncEventsRequestDTO request) {
        if (calls.get(userId, k -> new AtomicInteger()).incrementAndGet() > callLimit) {
            throw new AuthException("RATE_LIMITED", "Too many requests. Try again later");
        }
        List<Result> results = new ArrayList<>();
        for (ItemStateEventDTO event : request.events()) {
            results.add(applyOne(userId, event));
        }
        return new SyncEventsResponseDTO(results);
    }

    private Result applyOne(String userId, ItemStateEventDTO e) {
        String id = e.clientEventId();
        if (id == null || !isUuid(id)) {
            return new Result(id, "REJECTED", "INVALID_EVENT_ID");
        }
        Parsed p;
        try {
            p = parse(e);
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            return new Result(id, "REJECTED", "INVALID_EVENT");
        }
        // A retried batch changes nothing: an event id already seen is a DUPLICATE.
        if (events.existsByUserIdAndClientEventId(userId, id)) {
            return new Result(id, "DUPLICATE", null);
        }
        SrsState row = states.findByUserIdAndItemId(userId, e.itemId())
                .orElseGet(() -> new SrsState(userId, e.itemId()));
        merge(row, p);
        states.save(row);
        events.save(new SyncEvent(userId, id, clock.instant()));
        return new Result(id, "ACCEPTED", null);
    }

    /**
     * Conflict rules (PLAN 5.3): the later updatedAt wins the schedule; learnedAt is a one-way flag
     * that keeps the earliest value from either side and never clears.
     */
    static void merge(SrsState row, Parsed p) {
        boolean isNew = row.getUpdatedAt() == null;
        if (isNew || p.updatedAt().isAfter(row.getUpdatedAt())) {
            row.setStage(p.stage());
            row.setDueOn(p.dueOn());
            row.setLastResult(p.lastResult());
            row.setLastCorrectOn(p.lastCorrectOn());
            row.setUpdatedAt(p.updatedAt());
        }
        row.setLearnedAt(earliest(row.getLearnedAt(), p.learnedAt()));
    }

    private static Instant earliest(Instant a, Instant b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }

    record Parsed(int stage, LocalDate dueOn, Instant learnedAt, String lastResult, LocalDate lastCorrectOn,
                  Instant updatedAt) {
    }

    private static Parsed parse(ItemStateEventDTO e) {
        if (e.itemId() == null || !ITEM_ID.matcher(e.itemId()).matches()) throw new IllegalArgumentException("itemId");
        if (e.stage() == null || e.stage() < 0 || e.stage() > 6) throw new IllegalArgumentException("stage");
        if (e.lastResult() != null && !RESULTS.contains(e.lastResult())) throw new IllegalArgumentException("lastResult");
        if (e.updatedAt() == null) throw new IllegalArgumentException("updatedAt");
        return new Parsed(e.stage(), date(e.dueOn()), instant(e.learnedAt()), e.lastResult(), date(e.lastCorrectOn()),
                Instant.parse(e.updatedAt()));
    }

    private static LocalDate date(String s) {
        return s == null ? null : LocalDate.parse(s);
    }

    private static Instant instant(String s) {
        return s == null ? null : Instant.parse(s);
    }

    private static boolean isUuid(String s) {
        try {
            UUID.fromString(s);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ProgressResponseDTO progress(String userId, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        Page<SrsState> result = states.findByUserIdOrderByItemIdAsc(userId,
                PageRequest.of(Math.max(page, 0), safeSize));
        List<ProgressResponseDTO.Item> items = result.getContent().stream().map(s -> new ProgressResponseDTO.Item(
                s.getItemId(), s.getStage(), str(s.getDueOn()), str(s.getLearnedAt()), s.getLastResult(),
                str(s.getLastCorrectOn()), str(s.getUpdatedAt()))).toList();
        return new ProgressResponseDTO(states.countLearned(userId), result.getTotalElements(), result.getNumber(),
                safeSize, items);
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
