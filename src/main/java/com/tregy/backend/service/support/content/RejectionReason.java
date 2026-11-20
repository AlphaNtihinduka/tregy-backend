package com.tregy.backend.service.support.content;

/** Why the validator turned an item away. Stored per run (counts only) so prompts can be tuned. */
public enum RejectionReason {
    MISSING_FIELD,
    BAD_POS,
    TOO_SHORT,
    TOO_LONG,
    WRONG_LANGUAGE,
    DUPLICATE,
    NEAR_DUPLICATE,
    BAD_DISTRACTORS,
    BAD_ACCEPTED_VARIANTS,
    UNSAFE,
    TOO_HARD
}
