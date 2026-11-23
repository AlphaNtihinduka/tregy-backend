package com.tregy.backend.dto.response;
import java.time.Instant;
public record AuthResponseDTO(String accessToken,Instant accessTokenExpiresAt,String refreshToken,Instant refreshTokenExpiresAt,UserResponseDTO user,boolean deletionCancelled){}
