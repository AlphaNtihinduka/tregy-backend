package com.tregy.backend.service.impl;

import com.tregy.backend.dto.request.CreateGenerationRunRequestDTO;
import com.tregy.backend.dto.response.GenerationRunResponseDTO;
import com.tregy.backend.exception.AuthException;
import com.tregy.backend.model.ContentItem;
import com.tregy.backend.model.GenerationRun;
import com.tregy.backend.model.enums.ContentStatus;
import com.tregy.backend.model.enums.RunStatus;
import com.tregy.backend.repository.ContentItemRepository;
import com.tregy.backend.repository.GenerationRunRepository;
import com.tregy.backend.repository.ThemeRepository;
import com.tregy.backend.service.ContentGenerationService;
import com.tregy.backend.service.support.content.ContentValidator;
import com.tregy.backend.service.support.content.GeneratedItem;
import com.tregy.backend.service.support.content.GenerationRequest;
import com.tregy.backend.service.support.content.LlmClient;
import com.tregy.backend.service.support.content.RejectionReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ContentGenerationServiceImpl implements ContentGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ContentGenerationServiceImpl.class);
    /** Calls to the model per run: the first, plus top-ups when some items were rejected. */
    private static final int MAX_CALLS_PER_RUN = 3;

    private final LlmClient llm;
    private final ContentValidator validator;
    private final ContentItemRepository items;
    private final GenerationRunRepository runs;
    private final ThemeRepository themes;
    private final JsonMapper json;
    private final Clock clock;
    private final int maxItemsPerRun;
    private final long monthlyBudgetMicroUsd;
    private final long inputMicroUsdPerMillionTokens;
    private final long outputMicroUsdPerMillionTokens;

    public ContentGenerationServiceImpl(LlmClient llm, ContentValidator validator, ContentItemRepository items,
                                        GenerationRunRepository runs, ThemeRepository themes, JsonMapper json,
                                        Clock clock,
                                        @Value("${app.generation.max-items-per-run:100}") int maxItemsPerRun,
                                        @Value("${app.generation.monthly-budget-usd:50}") double monthlyBudgetUsd,
                                        @Value("${app.generation.input-usd-per-million-tokens:0}") double inputUsd,
                                        @Value("${app.generation.output-usd-per-million-tokens:0}") double outputUsd) {
        this.llm = llm;
        this.validator = validator;
        this.items = items;
        this.runs = runs;
        this.themes = themes;
        this.json = json;
        this.clock = clock;
        this.maxItemsPerRun = maxItemsPerRun;
        this.monthlyBudgetMicroUsd = Math.round(monthlyBudgetUsd * 1_000_000);
        this.inputMicroUsdPerMillionTokens = Math.round(inputUsd * 1_000_000);
        this.outputMicroUsdPerMillionTokens = Math.round(outputUsd * 1_000_000);
    }

    @Override
    @Transactional
    public GenerationRunResponseDTO run(String adminUserId, CreateGenerationRunRequestDTO request) {
        validateRequest(request);
        Instant now = clock.instant();
        GenerationRun run = newRun(adminUserId, request, now);

        long spentBefore = runs.spentSince(startOfMonth(now));
        if (spentBefore >= monthlyBudgetMicroUsd) {
            // Nothing is generated: the budget is already used up for the month.
            run.setStatus(RunStatus.STOPPED_BUDGET);
            run.setFinishedAt(now);
            return toResponse(runs.save(run));
        }

        Set<String> knownKeys = new HashSet<>(items.liveDedupeKeys(request.sourceLang(), request.targetLang()));
        Map<RejectionReason, Integer> rejections = new EnumMap<>(RejectionReason.class);
        List<ContentItem> accepted = new ArrayList<>();
        RunStatus status = RunStatus.COMPLETED;
        try {
            for (int call = 0; call < MAX_CALLS_PER_RUN && accepted.size() < request.count(); call++) {
                if (spentBefore + run.getCostMicroUsd() >= monthlyBudgetMicroUsd) {
                    status = RunStatus.STOPPED_BUDGET;
                    break;
                }
                LlmClient.Result result = llm.generate(new GenerationRequest(request.sourceLang(),
                        request.targetLang(), request.level(), request.theme(), request.kind(),
                        request.count() - accepted.size(), List.copyOf(
                                items.liveTargetTexts(request.sourceLang(), request.targetLang(), request.level(),
                                        request.theme()))));
                run.setInputTokens(run.getInputTokens() + result.inputTokens());
                run.setOutputTokens(run.getOutputTokens() + result.outputTokens());
                run.setCostMicroUsd(cost(run.getInputTokens(), run.getOutputTokens()));
                if (result.items().isEmpty()) {
                    break; // The stock is used up: nothing more to add to this bucket.
                }
                GenerationRequest asked = new GenerationRequest(request.sourceLang(), request.targetLang(),
                        request.level(), request.theme(), request.kind(), request.count(), List.of());
                for (GeneratedItem generated : result.items()) {
                    run.setGeneratedCount(run.getGeneratedCount() + 1);
                    if (accepted.size() >= request.count()) {
                        break;
                    }
                    ContentValidator.Verdict verdict = validator.validate(generated, asked, knownKeys);
                    if (verdict.accepted()) {
                        accepted.add(toItem(generated, request, verdict, run.getId(), now));
                    } else {
                        rejections.merge(verdict.rejection(), 1, Integer::sum);
                    }
                }
            }
        } catch (RuntimeException e) {
            // The class and run id are enough to investigate; the message could echo model output.
            log.warn("Generation run failed runId={} errorType={}", run.getId(), e.getClass().getSimpleName());
            status = RunStatus.FAILED;
        }

        // The run row must exist before the items that point at it.
        runs.saveAndFlush(run);
        items.saveAll(accepted);
        run.setAcceptedCount(accepted.size());
        run.setRejectedCount(rejections.values().stream().mapToInt(Integer::intValue).sum());
        run.setRejectionSummary(toJson(rejections.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue))));
        run.setStatus(status);
        run.setFinishedAt(clock.instant());
        return toResponse(runs.save(run));
    }

    @Override
    @Transactional(readOnly = true)
    public List<GenerationRunResponseDTO> recentRuns(int limit) {
        int size = Math.max(1, Math.min(limit, 100));
        return runs.findAllByOrderByCreatedAtDesc(PageRequest.of(0, size)).stream().map(this::toResponse).toList();
    }

    private void validateRequest(CreateGenerationRunRequestDTO r) {
        boolean pair = ("it".equals(r.sourceLang()) && "en".equals(r.targetLang()))
                || ("en".equals(r.sourceLang()) && "it".equals(r.targetLang()));
        if (!pair) {
            throw new AuthException("INVALID_DIRECTION", "Direction must be it to en or en to it");
        }
        if (!themes.existsById(r.theme())) {
            throw new AuthException("UNKNOWN_THEME", "Unknown theme");
        }
        if (r.count() > maxItemsPerRun) {
            throw new AuthException("RUN_TOO_LARGE", "A run may ask for at most " + maxItemsPerRun + " items");
        }
    }

    private GenerationRun newRun(String adminUserId, CreateGenerationRunRequestDTO r, Instant now) {
        GenerationRun run = new GenerationRun();
        run.setId(UUID.randomUUID().toString());
        run.setSourceLang(r.sourceLang());
        run.setTargetLang(r.targetLang());
        run.setLevel(r.level());
        run.setTheme(r.theme());
        run.setKind(r.kind());
        run.setRequestedCount(r.count());
        run.setStatus(RunStatus.RUNNING);
        run.setPromptVersion(llm.promptVersion());
        run.setModelId(llm.modelId());
        run.setCreatedBy(adminUserId);
        run.setCreatedAt(now);
        return run;
    }

    private ContentItem toItem(GeneratedItem g, CreateGenerationRunRequestDTO r, ContentValidator.Verdict v,
                               String runId, Instant now) {
        ContentItem item = new ContentItem();
        item.setId(UUID.randomUUID().toString());
        item.setSourceLang(r.sourceLang());
        item.setTargetLang(r.targetLang());
        item.setLevel(r.level());
        item.setTheme(r.theme());
        item.setKind(r.kind());
        item.setPos(g.pos());
        item.setSourceText(g.sourceText().strip());
        item.setTargetText(g.targetText().strip());
        item.setAcceptedVariants(toJson(g.acceptedVariants()));
        item.setDistractors(toJson(g.distractors()));
        item.setDifficulty(v.difficulty());
        item.setDedupeKey(v.dedupeKey());
        item.setStatus(ContentStatus.VALIDATED);
        item.setGenerationRunId(runId);
        item.setCreatedAt(now);
        return item;
    }

    private long cost(long inputTokens, long outputTokens) {
        return (inputTokens * inputMicroUsdPerMillionTokens + outputTokens * outputMicroUsdPerMillionTokens)
                / 1_000_000;
    }

    private static Instant startOfMonth(Instant now) {
        ZonedDateTime z = now.atZone(ZoneOffset.UTC);
        return z.withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private String toJson(Object value) {
        return json.writeValueAsString(value);
    }

    private GenerationRunResponseDTO toResponse(GenerationRun r) {
        Map<String, Integer> rejections = json.readValue(r.getRejectionSummary(), new TypeReference<>() {
        });
        return new GenerationRunResponseDTO(r.getId(), r.getSourceLang(), r.getTargetLang(), r.getLevel().name(),
                r.getTheme(), r.getKind().name(), r.getRequestedCount(), r.getGeneratedCount(), r.getAcceptedCount(),
                r.getRejectedCount(), rejections, r.getStatus().name(), r.getPromptVersion(), r.getModelId(),
                r.getCostMicroUsd(), r.getCreatedAt(), r.getFinishedAt());
    }
}
