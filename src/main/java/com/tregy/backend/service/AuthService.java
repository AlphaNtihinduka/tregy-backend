package com.tregy.backend.service;
import com.tregy.backend.dto.request.*; import com.tregy.backend.dto.response.UserResponseDTO; import com.tregy.backend.service.support.AuthSession;
public interface AuthService{UserResponseDTO register(RegisterRequestDTO request);void verifyEmail(String token);void resendVerification(String email);AuthSession login(LoginRequestDTO request);AuthSession refresh(String token);void logout(String token);void logoutAll(String userId);void forgotPassword(String email);void resetPassword(ResetPasswordRequestDTO request);}
