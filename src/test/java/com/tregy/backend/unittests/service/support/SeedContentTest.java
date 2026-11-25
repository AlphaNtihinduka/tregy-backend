package com.tregy.backend.unittests.service.support;

import com.tregy.backend.model.enums.ContentKind;
import com.tregy.backend.model.enums.LearningLevel;
import com.tregy.backend.service.support.content.ContentValidator;
import com.tregy.backend.service.support.content.FileLlmClient;
import com.tregy.backend.service.support.content.GeneratedItem;
import com.tregy.backend.service.support.content.GenerationRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs every hand-written seed file in {@code content-seed/} through the same validator that guards model
 * output, in both directions, with one shared duplicate set per direction (as the real bucket has). A typo,
 * a too-hard sentence in a beginner file or a repeated word fails here, with the file and the text named,
 * before anything is published. This does not replace a native speaker: it checks structure, not taste.
 */
class SeedContentTest {

    private static final List<String> THEMES = List.of("general", "travel", "work", "dating", "food");
    private static final String[][] DIRECTIONS = {{"en", "it"}, {"it", "en"}};

    private final JsonMapper json = JsonMapper.builder().build();
    private final ContentValidator validator = new ContentValidator();
    private final FileLlmClient client = new FileLlmClient("seed-test", "content-seed", json);

    @Test
    void shouldAcceptEverySeedItemInBothDirections() {
        List<String> problems = new ArrayList<>();
        int total = 0;
        for (String[] direction : DIRECTIONS) {
            Set<String> known = new HashSet<>();
            for (LearningLevel level : LearningLevel.values()) {
                for (String theme : THEMES) {
                    for (ContentKind kind : ContentKind.values()) {
                        GenerationRequest request = new GenerationRequest(direction[0], direction[1], level, theme,
                                kind, 1000, List.of());
                        for (GeneratedItem item : client.generate(request).items()) {
                            total++;
                            ContentValidator.Verdict verdict = validator.validate(item, request, known);
                            if (!verdict.accepted()) {
                                problems.add(direction[0] + ">" + direction[1] + " " + level + "/" + theme + "-"
                                        + kind.name().toLowerCase(Locale.ROOT) + ": '" + item.targetText()
                                        + "' rejected as " + verdict.rejection());
                            }
                        }
                    }
                }
            }
        }
        assertThat(total).as("seed items found").isGreaterThan(0);
        assertThat(problems).as("rejected seed items").isEmpty();
    }
}
