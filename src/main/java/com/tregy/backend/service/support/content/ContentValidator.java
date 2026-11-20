package com.tregy.backend.service.support.content;

import com.tregy.backend.model.enums.ContentKind;
import com.tregy.backend.model.enums.LearningLevel;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Checks every generated item in code. Nothing here asks the model whether it got things right: a
 * model's own claim about language, level or correctness is never trusted. An item that fails any
 * rule is rejected with one reason; an item that passes gets a difficulty from measurable features.
 */
@Component
public class ContentValidator {

    private static final Set<String> POS = Set.of("noun", "verb", "adj", "phrase");
    private static final Pattern PERSONAL_DATA = Pattern.compile("@|https?:|www\\.|\\d{5,}");
    static final int DISTRACTOR_COUNT = 3;

    private final Set<String> bannedTerms;

    public ContentValidator() {
        this.bannedTerms = loadBannedTerms();
    }

    public record Verdict(RejectionReason rejection, int difficulty, String dedupeKey) {
        public boolean accepted() {
            return rejection == null;
        }

        static Verdict reject(RejectionReason reason) {
            return new Verdict(reason, 0, null);
        }
    }

    /**
     * @param knownKeys dedupe keys already in the bucket's direction plus those accepted earlier in
     *                  this run. The key of an accepted item is added so later items in the run see it.
     */
    public Verdict validate(GeneratedItem item, GenerationRequest request, Set<String> knownKeys) {
        Optional<RejectionReason> problem = firstProblem(item, request);
        if (problem.isPresent()) {
            return Verdict.reject(problem.get());
        }
        String key = TextRules.key(item.targetText());
        if (knownKeys.contains(key)) {
            return Verdict.reject(RejectionReason.DUPLICATE);
        }
        if (key.length() >= 6 && knownKeys.stream()
                .anyMatch(other -> Math.abs(other.length() - key.length()) <= 1
                        && TextRules.editDistance(other, key) <= 1)) {
            return Verdict.reject(RejectionReason.NEAR_DUPLICATE);
        }
        int difficulty = difficultyOf(item.targetText(), request.kind());
        if (difficulty > bandMax(request.level())) {
            return Verdict.reject(RejectionReason.TOO_HARD);
        }
        knownKeys.add(key);
        return new Verdict(null, difficulty, key);
    }

    private Optional<RejectionReason> firstProblem(GeneratedItem item, GenerationRequest request) {
        if (blank(item.pos()) || blank(item.sourceText()) || blank(item.targetText())
                || item.acceptedVariants() == null || item.distractors() == null) {
            return Optional.of(RejectionReason.MISSING_FIELD);
        }
        if (!POS.contains(item.pos()) || (request.kind() != ContentKind.WORD) != "phrase".equals(item.pos())) {
            return Optional.of(RejectionReason.BAD_POS);
        }
        if (item.sourceText().length() > 300 || item.targetText().length() > 300) {
            return Optional.of(RejectionReason.TOO_LONG);
        }
        int words = TextRules.words(item.targetText()).size();
        if (words < minWords(request.kind())) {
            return Optional.of(RejectionReason.TOO_SHORT);
        }
        if (words > maxWords(request.kind(), request.level())
                || TextRules.words(item.sourceText()).size() > maxWords(request.kind(), request.level()) + 3) {
            return Optional.of(RejectionReason.TOO_LONG);
        }
        if (!TextRules.looksLike(item.targetText(), request.targetLang())
                || !TextRules.looksLike(item.sourceText(), request.sourceLang())) {
            return Optional.of(RejectionReason.WRONG_LANGUAGE);
        }
        if (unsafe(item)) {
            return Optional.of(RejectionReason.UNSAFE);
        }
        if (!goodAccepted(item)) {
            return Optional.of(RejectionReason.BAD_ACCEPTED_VARIANTS);
        }
        if (!goodDistractors(item)) {
            return Optional.of(RejectionReason.BAD_DISTRACTORS);
        }
        return Optional.empty();
    }

    /** Accepted answers must be real text and include the canonical target. */
    private static boolean goodAccepted(GeneratedItem item) {
        if (item.acceptedVariants().isEmpty() || item.acceptedVariants().stream().anyMatch(ContentValidator::blank)) {
            return false;
        }
        String target = TextRules.key(item.targetText());
        return item.acceptedVariants().stream().anyMatch(v -> TextRules.key(v).equals(target));
    }

    /** Exactly three wrong options: real, distinct, none equal to the answer or to an accepted variant. */
    private static boolean goodDistractors(GeneratedItem item) {
        List<String> distractors = item.distractors();
        if (distractors.size() != DISTRACTOR_COUNT || distractors.stream().anyMatch(ContentValidator::blank)) {
            return false;
        }
        Set<String> keys = distractors.stream().map(TextRules::key).collect(Collectors.toSet());
        if (keys.size() != DISTRACTOR_COUNT) {
            return false;
        }
        Set<String> correct = new HashSet<>();
        correct.add(TextRules.key(item.targetText()));
        item.acceptedVariants().forEach(v -> correct.add(TextRules.key(v)));
        return keys.stream().noneMatch(correct::contains);
    }

    private boolean unsafe(GeneratedItem item) {
        List<String> texts = new java.util.ArrayList<>(List.of(item.sourceText(), item.targetText()));
        texts.addAll(item.acceptedVariants());
        texts.addAll(item.distractors());
        for (String text : texts) {
            if (PERSONAL_DATA.matcher(text).find()) {
                return true;
            }
            if (TextRules.words(text).stream().anyMatch(bannedTerms::contains)) {
                return true;
            }
        }
        return false;
    }

    static int minWords(ContentKind kind) {
        return switch (kind) {
            case WORD -> 1;
            case PHRASE -> 2;
            case SENTENCE -> 4;
        };
    }

    static int maxWords(ContentKind kind, LearningLevel level) {
        return switch (kind) {
            case WORD -> 3;
            case PHRASE -> switch (level) {
                case BEGINNER -> 5;
                case INTERMEDIATE -> 7;
                case ADVANCED -> 10;
            };
            case SENTENCE -> switch (level) {
                case BEGINNER -> 8;
                case INTERMEDIATE -> 14;
                case ADVANCED -> 22;
            };
        };
    }

    /** Each level owns a third of the 1-100 difficulty scale. */
    static int bandMax(LearningLevel level) {
        return switch (level) {
            case BEGINNER -> 33;
            case INTERMEDIATE -> 66;
            case ADVANCED -> 100;
        };
    }

    /** Measured from the text itself: kind, number of words and the longest word. Never the model's claim. */
    public static int difficultyOf(String targetText, ContentKind kind) {
        List<String> words = TextRules.words(targetText);
        int base = switch (kind) {
            case WORD -> 5;
            case PHRASE -> 14;
            case SENTENCE -> 34;
        };
        int longest = words.stream().mapToInt(String::length).max().orElse(0);
        int score = base + 4 * Math.max(0, words.size() - 1) + 2 * Math.max(0, longest - 5);
        return Math.min(100, Math.max(1, score));
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static Set<String> loadBannedTerms() {
        try (var in = new ClassPathResource("content-banned-terms.txt").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .map(l -> l.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read content-banned-terms.txt", e);
        }
    }
}
