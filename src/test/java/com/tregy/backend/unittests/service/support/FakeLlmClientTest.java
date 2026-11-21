package com.tregy.backend.unittests.service.support;

import com.tregy.backend.model.enums.ContentKind;
import com.tregy.backend.model.enums.LearningLevel;
import com.tregy.backend.service.support.content.FakeLlmClient;
import com.tregy.backend.service.support.content.GeneratedItem;
import com.tregy.backend.service.support.content.GenerationRequest;
import com.tregy.backend.service.support.content.LlmClient;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FakeLlmClientTest {

    private final FakeLlmClient fake = new FakeLlmClient("fake-model");

    private LlmClient.Result ask(String theme, ContentKind kind, int count, String source, String target) {
        return fake.generate(new GenerationRequest(source, target, LearningLevel.BEGINNER, theme, kind, count, List.of()));
    }

    @Test
    void shouldGiveEveryPhraseThreeDistinctDistractorsEvenThoughThemesHoldFewPhrases() {
        for (String theme : List.of("general", "travel", "work", "food", "dating")) {
            for (GeneratedItem item : ask(theme, ContentKind.PHRASE, 10, "en", "it").items()) {
                assertEquals(3, item.distractors().size(), theme + ": " + item.targetText());
                assertEquals(3, new HashSet<>(item.distractors()).size());
                assertFalse(item.distractors().contains(item.targetText()));
            }
        }
    }

    @Test
    void shouldWorkInBothDirectionsAndSkipTextsAlreadyKnown() {
        GeneratedItem first = ask("food", ContentKind.WORD, 1, "it", "en").items().get(0);
        LlmClient.Result again = fake.generate(new GenerationRequest("it", "en", LearningLevel.BEGINNER, "food",
                ContentKind.WORD, 1, List.of(first.targetText())));
        assertNotEquals(first.targetText(), again.items().get(0).targetText());
        assertFalse(ask("food", ContentKind.WORD, 3, "en", "it").items().isEmpty());
    }
}
