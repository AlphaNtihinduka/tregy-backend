package com.tregy.backend.service;

import com.tregy.backend.dto.request.SyncEventsRequestDTO;
import com.tregy.backend.dto.response.ProgressResponseDTO;
import com.tregy.backend.dto.response.SyncEventsResponseDTO;

public interface SyncService {

    SyncEventsResponseDTO apply(String userId, SyncEventsRequestDTO request);

    ProgressResponseDTO progress(String userId, int page, int size);
}
