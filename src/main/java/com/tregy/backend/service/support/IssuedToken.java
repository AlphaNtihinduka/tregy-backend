package com.tregy.backend.service.support;

import java.time.Instant;

// A freshly issued raw token (access JWT or refresh token) and when it expires.
public record IssuedToken(String token, Instant expiresAt) {

    // The raw token is a credential: keep it out of any accidental log of this record.
    @Override
    public String toString() {
        return "IssuedToken[expiresAt=" + expiresAt + "]";
    }
}

