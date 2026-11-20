package com.tregy.backend.dto.response;

import java.util.List;

public record ProgressResponseDTO(long learned, long total, int page, int size, List<Item> items) {

    public record Item(String itemId, int stage, String dueOn, String learnedAt, String lastResult,
                       String lastCorrectOn, String updatedAt) {
    }
}
