package com.tregy.backend.unittests.service.support;

import com.tregy.backend.model.ContentItem;
import com.tregy.backend.model.enums.ContentKind;
import com.tregy.backend.model.enums.ContentStatus;
import com.tregy.backend.model.enums.LearningLevel;
import com.tregy.backend.service.support.content.ReviewCsv;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReviewCsvTest {

    private static ContentItem item(String source, String target) {
        ContentItem i = new ContentItem();
        i.setId("id-1");
        i.setSourceLang("it");
        i.setTargetLang("en");
        i.setLevel(LearningLevel.BEGINNER);
        i.setTheme("food");
        i.setKind(ContentKind.WORD);
        i.setSourceText(source);
        i.setTargetText(target);
        i.setDifficulty(12);
        i.setStatus(ContentStatus.VALIDATED);
        i.setAcceptedVariants("[]");
        return i;
    }

    @Test
    void shouldQuoteCommasQuotesAndNewlines() {
        String csv = ReviewCsv.of(List.of(item("la \"zuppa\", calda", "soup\nhot")), json -> List.of("soup", "broth"));
        assertTrue(csv.contains("\"la \"\"zuppa\"\", calda\""));
        assertTrue(csv.contains("\"soup\nhot\""));
        assertTrue(csv.contains("soup | broth"));
    }

    @Test
    void shouldDefuseTextAThatASpreadsheetWouldRunAsAFormula() {
        String csv = ReviewCsv.of(List.of(item("=HYPERLINK(\"http://x\")", "+cmd")), json -> List.of());
        assertTrue(csv.contains("'=HYPERLINK"));
        assertTrue(csv.contains("'+cmd"));
        assertFalse(csv.contains(",=HYPERLINK"));
    }

    @Test
    void shouldKeepAccentedLettersAsTheyAre() {
        assertTrue(ReviewCsv.of(List.of(item("è già", "it is already")), json -> List.of()).contains("è già"));
    }
}
