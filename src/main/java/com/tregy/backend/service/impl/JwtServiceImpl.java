package com.tregy.backend.service.impl;

import com.tregy.backend.model.AppUser;
import com.tregy.backend.service.JwtService;
import com.tregy.backend.service.support.AccessTokenClaims;
import com.tregy.backend.service.support.IssuedToken;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

/**
 * HS256 access tokens (PLAN Â§7, ADR 0005). Claims: sub = user id, tv = token version,
 * iat, exp, iss. No org id or org role: those are read from membership on every
 * request, so removing a member takes effect at once. Time comes from the injected
 * Clock so expiry is testable.
 */
@Service
public class JwtServiceImpl implements JwtService {

    static final String ISSUER = "tregy";
    static final String TOKEN_VERSION_CLAIM = "tv";
    static final String PLATFORM_ROLE_CLAIM = "pr";

    // HS256 needs at least a 256-bit key.
    private static final int MIN_SECRET_BYTES = 32;
    private static final Set<String> KNOWN_PLACEHOLDERS = Set.of(
            "replace_with_secure_jwt_secret",
            "change_me",
            "changeme"
    );

    private final SecretKey signingKey;
    private final Duration accessTokenLifetime;
    private final Clock clock;

    public JwtServiceImpl(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-expiration-ms}") long accessExpirationMs,
            Clock clock
    ) {
        this.signingKey = Keys.hmacShaKeyFor(requireSecureSecret(secret).getBytes(StandardCharsets.UTF_8));
        if (accessExpirationMs <= 0) {
            throw new IllegalStateException("JWT_ACCESS_EXPIRATION_MS must be positive");
        }
        this.accessTokenLifetime = Duration.ofMillis(accessExpirationMs);
        this.clock = clock;
    }

    // Fail at startup rather than sign tokens with a missing or guessable key.
    static String requireSecureSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is not set. Refusing to start without a signing key.");
        }
        if (KNOWN_PLACEHOLDERS.contains(secret.strip())) {
            throw new IllegalStateException(
                    "JWT_SECRET is still a placeholder from .env.example. Generate one with `openssl rand -base64 48`.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET is too short for HS256 (needs at least " + MIN_SECRET_BYTES + " bytes).");
        }
        return secret;
    }

    @Override
    public IssuedToken generateAccessToken(AppUser appUser) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(accessTokenLifetime);
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject(appUser.getId())
                .claim(TOKEN_VERSION_CLAIM, appUser.getTokenVersion())
                .claim(PLATFORM_ROLE_CLAIM, appUser.getPlatformRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    @Override
    public AccessTokenClaims parseAccessToken(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(ISSUER)
                .clock(() -> Date.from(Instant.now(clock)))
                .build()
                .parseSignedClaims(token);
        // Only HS256 is ever issued. A long enough key would also verify HS384/HS512
        // tokens; pin the algorithm so no other one is accepted.
        if (!Jwts.SIG.HS256.getId().equals(jws.getHeader().getAlgorithm())) {
            throw new JwtException("Access token is not signed with HS256");
        }
        Claims claims = jws.getPayload();

        Integer tokenVersion = claims.get(TOKEN_VERSION_CLAIM, Integer.class);
        String platformRole = claims.get(PLATFORM_ROLE_CLAIM, String.class);
        if (claims.getSubject() == null || claims.getSubject().isBlank() || tokenVersion == null || platformRole == null) {
            throw new JwtException("Access token is missing its subject or token version");
        }
        return new AccessTokenClaims(claims.getSubject(), tokenVersion, platformRole);
    }
}
