package com.tregy.backend.unittests.service.support;

import com.tregy.backend.model.enums.ContentKind;
import com.tregy.backend.model.enums.LearningLevel;
import com.tregy.backend.service.support.content.FileLlmClient;
import com.tregy.backend.service.support.content.GeneratedItem;
import com.tregy.backend.service.support.content.GenerationRequest;
import com.tregy.backend.service.support.content.LlmClient;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FileLlmClientTest {

    private final FileLlmClient client = new FileLlmClient("file-test", "content-seed", JsonMapper.builder().build());

    private GenerationRequest request(String source, String target, int count, List<String> existing) {
        return new GenerationRequest(source, target, LearningLevel.BEGINNER, "general", ContentKind.WORD, count, existing);
    }

    @Test
    void shouldServeTheSameEntryInBothDirections() {
        GeneratedItem toItalian = client.generate(request("en", "it", 1, List.of())).items().get(0);
        GeneratedItem toEnglish = client.generate(request("it", "en", 1, List.of())).items().get(0);

        assertThat(toItalian.sourceText()).isEqualTo("house");
        assertThat(toItalian.targetText()).isEqualTo("la casa");
        assertThat(toItalian.acceptedVariants()).contains("la casa", "casa");
        assertThat(toEnglish.sourceText()).isEqualTo("la casa");
        assertThat(toEnglish.targetText()).isEqualTo("house");
    }

    @Test
    void shouldSkipTargetsTheBucketAlreadyHolds() {
        List<GeneratedItem> items = client.generate(request("en", "it", 3, List.of("la casa", "L'ACQUA"))).items();

        assertThat(items).extracting(GeneratedItem::targetText).doesNotContain("la casa", "l'acqua");
        assertThat(items).hasSize(3);
    }

    @Test
    void shouldGiveThreeDistinctWrongOptionsOfTheSameKind() {
        LlmClient.Result result = client.generate(request("en", "it", 5, List.of()));

        for (GeneratedItem item : result.items()) {
            assertThat(item.distractors()).hasSize(3).doesNotHaveDuplicates().doesNotContainAnyElementsOf(item.acceptedVariants());
        }
        assertThat(result.inputTokens()).isZero();
        assertThat(result.outputTokens()).isZero();
    }

    @Test
    void shouldReturnNothingWhenThereIsNoFileForTheBucket() {
        GenerationRequest none = new GenerationRequest("en", "it", LearningLevel.BEGINNER, "general",
                ContentKind.SENTENCE, 5, List.of());

        assertThat(client.generate(none).items()).isEmpty();
    }
}
