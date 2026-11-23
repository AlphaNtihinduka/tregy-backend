package com.tregy.backend.dto.response;

import java.time.Instant;

/** The moment the account will be deleted unless the owner signs in again before then. */
public record DeletionScheduledResponseDTO(Instant deletesAt) {}
