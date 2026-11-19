package com.tregy.backend.service;

import com.tregy.backend.model.AppUser;
import com.tregy.backend.service.support.AccessTokenClaims;
import com.tregy.backend.service.support.IssuedToken;

public interface JwtService {

    // Short-lived (JWT_ACCESS_EXPIRATION_MS) access token: sub = user id, tv = token version.
    IssuedToken generateAccessToken(AppUser appUser);

    // Verifies signature, issuer and expiry; throws io.jsonwebtoken.JwtException (or
    // IllegalArgumentException) for anything else.
    AccessTokenClaims parseAccessToken(String token);
}

