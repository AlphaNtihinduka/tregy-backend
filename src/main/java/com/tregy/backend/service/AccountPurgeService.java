package com.tregy.backend.service;

/** Deletes the accounts whose deletion grace period has ended (ADR 0009). */
public interface AccountPurgeService {
    /** Deletes every account whose request is older than the grace period. Returns how many were deleted. */
    int purgeDue();
}
