package com.tregy.backend.dto.response;

import java.util.List;
import java.util.Map;

/** A pack in the exact shape the phone stores: texts per language code, no user data. */
public record PackResponseDTO(String packId, long version, String theme, String level, List<String> languages,
                              List<Entry> entries) {

    public record Entry(String id, String pos, int difficulty, Map<String, String> text,
                        Map<String, List<String>> accepted) {
    }
}
