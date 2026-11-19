package com.tregy.backend.service.support;
import com.tregy.backend.dto.response.UserResponseDTO;
public record AuthSession(IssuedToken accessToken,IssuedToken refreshToken,UserResponseDTO user){}
