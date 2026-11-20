package com.tregy.backend.unittests.service.support;

import com.tregy.backend.model.enums.ContentKind;
import com.tregy.backend.model.enums.LearningLevel;
import com.tregy.backend.service.support.content.ContentValidator;
import com.tregy.backend.service.support.content.GeneratedItem;
import com.tregy.backend.service.support.content.GenerationRequest;
import com.tregy.backend.service.support.content.RejectionReason;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Adversarial fixtures: each bad class must be rejected for the right reason, in code. */
class ContentValidatorTest {

    private final ContentValidator validator = new ContentValidator();

    private static GenerationRequest request(ContentKind kind, LearningLevel level) {
        return new GenerationRequest("it", "en", level, "food", kind, 10, List.of());
    }

    private static GeneratedItem word(String source, String target, List<String> accepted, List<String> distractors) {
        return new GeneratedItem("noun", source, target, accepted, distractors);
    }

    private static GeneratedItem goodWord() {
        return word("la zuppa", "soup", List.of("soup"), List.of("salad", "knife", "plate"));
    }

    private RejectionReason reject(GeneratedItem item, GenerationRequest req, Set<String> known) {
        ContentValidator.Verdict verdict = validator.validate(item, req, known);
        assertFalse(verdict.accepted(), "should have been rejected");
        return verdict.rejection();
    }

    @Test
    void shouldAcceptAGoodItemAndAssignADifficultyInsideTheBand() {
        Set<String> known = new HashSet<>();
        ContentValidator.Verdict verdict = validator.validate(goodWord(), request(ContentKind.WORD, LearningLevel.BEGINNER), known);
        assertTrue(verdict.accepted());
        assertTrue(verdict.difficulty() >= 1 && verdict.difficulty() <= 33);
        assertEquals("soup", verdict.dedupeKey());
        assertTrue(known.contains("soup"), "an accepted key is remembered for the rest of the run");
    }

    @Test
    void shouldRejectMissingFieldsAndBadPartOfSpeech() {
        GenerationRequest req = request(ContentKind.WORD, LearningLevel.BEGINNER);
        assertEquals(RejectionReason.MISSING_FIELD,
                reject(word(" ", "soup", List.of("soup"), List.of("a", "b", "c")), req, new HashSet<>()));
        assertEquals(RejectionReason.MISSING_FIELD,
                reject(new GeneratedItem("noun", "la zuppa", "soup", null, List.of()), req, new HashSet<>()));
        assertEquals(RejectionReason.BAD_POS,
                reject(new GeneratedItem("pronoun", "la zuppa", "soup", List.of("soup"), List.of("a", "b", "c")),
                        req, new HashSet<>()));
        // A word item may not claim to be a phrase.
        assertEquals(RejectionReason.BAD_POS,
                reject(new GeneratedItem("phrase", "la zuppa", "soup", List.of("soup"), List.of("a", "b", "c")),
                        req, new HashSet<>()));
    }

    @Test
    void shouldRejectTextThatIsTooShortOrTooLongForTheKindAndLevel() {
        GenerationRequest phrases = request(ContentKind.PHRASE, LearningLevel.BEGINNER);
        assertEquals(RejectionReason.TOO_SHORT, reject(
                new GeneratedItem("phrase", "ciao", "hello", List.of("hello"), List.of("a b", "c d", "e f")),
                phrases, new HashSet<>()));
        assertEquals(RejectionReason.TOO_LONG, reject(
                new GeneratedItem("phrase", "una frase molto lunga", "this is a far too long phrase for you",
                        List.of("this is a far too long phrase for you"), List.of("a b", "c d", "e f")),
                phrases, new HashSet<>()));
    }

    @Test
    void shouldRejectTextInTheWrongLanguage() {
        GenerationRequest req = request(ContentKind.PHRASE, LearningLevel.BEGINNER);
        // The target should be English but is Italian.
        assertEquals(RejectionReason.WRONG_LANGUAGE, reject(
                new GeneratedItem("phrase", "il conto per favore", "il conto per favore",
                        List.of("il conto per favore"), List.of("a b", "c d", "e f")), req, new HashSet<>()));
    }

    @Test
    void shouldRejectDuplicatesAndNearDuplicates() {
        GenerationRequest req = request(ContentKind.WORD, LearningLevel.BEGINNER);
        Set<String> known = new HashSet<>(Set.of("soup", "restaurant"));
        assertEquals(RejectionReason.DUPLICATE, reject(goodWord(), req, known));
        // One letter apart from a known word of similar length (a plural, a typo).
        assertEquals(RejectionReason.NEAR_DUPLICATE, reject(
                word("i ristoranti", "restaurants", List.of("restaurants"), List.of("salad", "knife", "plate")),
                req, known));
    }

    @Test
    void shouldRejectBadDistractors() {
        GenerationRequest req = request(ContentKind.WORD, LearningLevel.BEGINNER);
        // Wrong count, repeated, equal to the answer, equal to an accepted variant, blank.
        for (List<String> bad : List.of(
                List.of("salad", "knife"),
                List.of("salad", "salad", "plate"),
                List.of("salad", "SOUP", "plate"),
                List.of("salad", "knife", " "))) {
            assertEquals(RejectionReason.BAD_DISTRACTORS,
                    reject(word("la zuppa", "soup", List.of("soup"), bad), req, new HashSet<>()), bad.toString());
        }
        assertEquals(RejectionReason.BAD_DISTRACTORS, reject(
                word("la zuppa", "soup", List.of("soup", "broth"), List.of("salad", "broth", "plate")),
                req, new HashSet<>()));
    }

    @Test
    void shouldRejectAcceptedVariantsThatMissTheCanonicalAnswer() {
        GenerationRequest req = request(ContentKind.WORD, LearningLevel.BEGINNER);
        assertEquals(RejectionReason.BAD_ACCEPTED_VARIANTS, reject(
                word("la zuppa", "soup", List.of(), List.of("salad", "knife", "plate")), req, new HashSet<>()));
        assertEquals(RejectionReason.BAD_ACCEPTED_VARIANTS, reject(
                word("la zuppa", "soup", List.of("broth"), List.of("salad", "knife", "plate")), req, new HashSet<>()));
    }

    @Test
    void shouldRejectUnsafeContent() {
        GenerationRequest req = request(ContentKind.WORD, LearningLevel.BEGINNER);
        assertEquals(RejectionReason.UNSAFE, reject(
                word("la merda", "shit", List.of("shit"), List.of("salad", "knife", "plate")), req, new HashSet<>()));
        // A banned word hidden in a distractor still counts.
        assertEquals(RejectionReason.UNSAFE, reject(
                word("la zuppa", "soup", List.of("soup"), List.of("salad", "fuck", "plate")), req, new HashSet<>()));
        // Personal data: an address and a long number.
        assertEquals(RejectionReason.UNSAFE, reject(
                word("la zuppa", "soup@example.com", List.of("soup@example.com"), List.of("salad", "knife", "plate")),
                req, new HashSet<>()));
        assertEquals(RejectionReason.UNSAFE, reject(
                word("la zuppa", "soup 3331234567", List.of("soup 3331234567"), List.of("salad", "knife", "plate")),
                req, new HashSet<>()));
    }

    @Test
    void shouldRejectItemsMeasuredAsTooHardForTheLevel() {
        // A five-word phrase with a very long word scores above the beginner band when measured.
        GeneratedItem hard = new GeneratedItem("phrase", "mi dispiace davvero molto",
                "extraordinarily unfortunate circumstances arose",
                List.of("extraordinarily unfortunate circumstances arose"), List.of("a b", "c d", "e f"));
        assertEquals(RejectionReason.TOO_HARD,
                reject(hard, request(ContentKind.PHRASE, LearningLevel.BEGINNER), new HashSet<>()));
    }

    @Test
    void shouldMeasureDifficultyFromTheTextNotTheModel() {
        assertTrue(ContentValidator.difficultyOf("soup", ContentKind.WORD)
                < ContentValidator.difficultyOf("the bill please", ContentKind.PHRASE));
        assertTrue(ContentValidator.difficultyOf("the bill please", ContentKind.PHRASE)
                < ContentValidator.difficultyOf("could you bring us the bill please", ContentKind.SENTENCE));
        assertEquals(100, ContentValidator.difficultyOf("x ".repeat(80), ContentKind.SENTENCE));
    }
}
