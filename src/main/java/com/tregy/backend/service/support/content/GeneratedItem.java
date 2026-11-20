package com.tregy.backend.service.support.content;

import java.util.List;

/**
 * An item as the model returned it: untrusted until the validator accepts it. {@code pos} is one of
 * noun, verb, adj or phrase (the app's vocabulary classes).
 */
public record GeneratedItem(String pos, String sourceText, String targetText, List<String> acceptedVariants,
                            List<String> distractors) {
}
