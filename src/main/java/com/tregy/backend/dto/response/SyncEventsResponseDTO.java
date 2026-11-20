package com.tregy.backend.dto.response;

import java.util.List;

public record SyncEventsResponseDTO(List<Result> results) {

    /** status is ACCEPTED, DUPLICATE or REJECTED; code is set only for REJECTED. */
    public record Result(String clientEventId, String status, String code) {
    }
}
