package com.tregy.backend.service.support.content;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Small text helpers shared by the validator: keys, word counts and a rough language check. */
final class TextRules {

    // Function words that belong to only one of the two languages. Words both languages share
    // (a, in, e, no) are left out on purpose so they cannot tip the check the wrong way.
    private static final Set<String> EN_ONLY = Set.of("the", "is", "an", "of", "to", "you", "am", "it", "for", "at",
            "my", "we", "and", "with", "please", "would", "like", "are", "this", "have", "was", "do", "not");
    private static final Set<String> IT_ONLY = Set.of("il", "lo", "la", "le", "gli", "un", "una", "di", "che", "sono",
            "per", "con", "mi", "ti", "ci", "non", "del", "al", "alla", "dei", "vuoi", "oggi", "stai", "ho", "hai");

    private TextRules() {
    }

    /** Lower case, accents folded, punctuation dropped, spaces collapsed: the duplicate key. */
    static String key(String text) {
        String folded = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return folded.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").strip();
    }

    static List<String> words(String text) {
        String key = key(text);
        return key.isEmpty() ? List.of() : Arrays.asList(key.split(" "));
    }

    /** True unless the text is clearly in the other language. Single words are not judged. */
    static boolean looksLike(String text, String lang) {
        List<String> words = words(text);
        if (words.size() < 2) {
            return true;
        }
        long en = words.stream().filter(EN_ONLY::contains).count();
        long it = words.stream().filter(IT_ONLY::contains).count();
        return "it".equals(lang) ? en <= it : it <= en;
    }

    static int editDistance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            int diagonal = previous[0];
            previous[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int above = previous[j];
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                previous[j] = Math.min(Math.min(previous[j] + 1, previous[j - 1] + 1), diagonal + cost);
                diagonal = above;
            }
        }
        return previous[b.length()];
    }
}
