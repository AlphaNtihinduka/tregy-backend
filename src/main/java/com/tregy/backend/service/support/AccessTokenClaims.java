package com.tregy.backend.service.support;

// The claims of a verified access JWT: sub (user id) and tv (token version).
public record AccessTokenClaims(String userId, int tokenVersion, String platformRole) {
}
