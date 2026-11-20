package com.tregy.backend.service.support.content;

import com.tregy.backend.model.ContentItem;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Writes items as CSV for a native speaker to check in a spreadsheet. The last columns are left
 * empty for the reviewer's verdict and notes. Cells that a spreadsheet would read as a formula get
 * a leading apostrophe, so a generated text can never run as one.
 */
public final class ReviewCsv {

    private static final String HEADER =
            "id,source_lang,target_lang,level,theme,kind,difficulty,status,source_text,target_text,accepted,verdict,notes";

    private ReviewCsv() {
    }

    public static String of(List<ContentItem> items, java.util.function.Function<String, List<String>> accepted) {
        StringBuilder out = new StringBuilder(HEADER).append("\r\n");
        for (ContentItem i : items) {
            out.append(String.join(",",
                    cell(i.getId()), cell(i.getSourceLang()), cell(i.getTargetLang()), cell(i.getLevel().name()),
                    cell(i.getTheme()), cell(i.getKind().name()), String.valueOf(i.getDifficulty()),
                    cell(i.getStatus().name()), cell(i.getSourceText()), cell(i.getTargetText()),
                    cell(accepted.apply(i.getAcceptedVariants()).stream().collect(Collectors.joining(" | "))),
                    "", "")).append("\r\n");
        }
        return out.toString();
    }

    static String cell(String value) {
        String text = value == null ? "" : value;
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
