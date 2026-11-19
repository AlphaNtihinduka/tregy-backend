package com.tregy.backend.controller;
import com.tregy.backend.dto.request.*;import com.tregy.backend.dto.response.*;import com.tregy.backend.service.AuthService;import com.tregy.backend.service.support.*;import jakarta.validation.Valid;import org.springframework.http.*;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/auth")
public class AuthController{
 private final AuthService service;private final AuthRateLimiter limiter;
 public AuthController(AuthService service,AuthRateLimiter limiter){this.service=service;this.limiter=limiter;}
 @PostMapping("/register") ResponseEntity<UserResponseDTO> register(@Valid @RequestBody RegisterRequestDTO r){limiter.checkRegister(r.email());return ResponseEntity.status(201).body(service.register(r));}
 @PostMapping("/verify-email") ResponseEntity<Void> verify(@Valid @RequestBody TokenRequestDTO r){service.verifyEmail(r.token());return ResponseEntity.noContent().build();}
 @PostMapping("/resend-verification") ResponseEntity<Void> resend(@Valid @RequestBody EmailRequestDTO r){limiter.checkRecovery("resend",r.email());service.resendVerification(r.email());return ResponseEntity.accepted().build();}
 @PostMapping("/login") AuthResponseDTO login(@Valid @RequestBody LoginRequestDTO r){limiter.checkLogin(r.email());return response(service.login(r));}
 @PostMapping("/refresh") AuthResponseDTO refresh(@Valid @RequestBody RefreshRequestDTO r){return response(service.refresh(r.refreshToken()));}
 @PostMapping("/logout") ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequestDTO r){service.logout(r.refreshToken());return ResponseEntity.noContent().build();}
 @PostMapping("/logout-all") ResponseEntity<Void> logoutAll(@AuthenticationPrincipal AppUserPrincipal p){service.logoutAll(p.userId());return ResponseEntity.noContent().build();}
 @PostMapping("/forgot-password") ResponseEntity<Void> forgot(@Valid @RequestBody EmailRequestDTO r){limiter.checkRecovery("forgot",r.email());service.forgotPassword(r.email());return ResponseEntity.accepted().build();}
 @PostMapping("/reset-password") ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequestDTO r){service.resetPassword(r);return ResponseEntity.noContent().build();}
 private static AuthResponseDTO response(AuthSession s){return new AuthResponseDTO(s.accessToken().token(),s.accessToken().expiresAt(),s.refreshToken().token(),s.refreshToken().expiresAt(),s.user());}
}
