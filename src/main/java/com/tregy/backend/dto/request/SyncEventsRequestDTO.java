package com.tregy.backend.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** A batch of events. Each event is validated on its own so one bad event does not sink the rest. */
public record SyncEventsRequestDTO(@NotEmpty @Size(max = 100) List<ItemStateEventDTO> events) {

    /**
     * The phone's state for one item after an answer. Dates are strings and parsed per event so a
     * malformed one is REJECTED alone. The server never re-derives the schedule: the device is
     * authoritative, and conflicts are settled by updatedAt (ADR 0007).
     */
    public record ItemStateEventDTO(String clientEventId, String itemId, Integer stage, String dueOn,
                                    String learnedAt, String lastResult, String lastCorrectOn, String updatedAt) {
    }
}
