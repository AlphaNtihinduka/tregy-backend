package com.tregy.backend.service.impl;
import com.tregy.backend.dto.request.DeleteAccountRequestDTO;import com.tregy.backend.dto.request.UpdateMeRequestDTO;import com.tregy.backend.dto.response.DeletionScheduledResponseDTO;import com.tregy.backend.dto.response.UserResponseDTO;import com.tregy.backend.service.RefreshTokenService;import org.springframework.beans.factory.annotation.Value;import java.time.Clock;import java.time.Duration;import java.time.Instant;import com.tregy.backend.exception.AuthException;import com.tregy.backend.model.AppUser;import com.tregy.backend.repository.AppUserRepository;import com.tregy.backend.service.MeService;import com.tregy.backend.service.support.AuthRateLimiter;import org.slf4j.Logger;import org.slf4j.LoggerFactory;import org.springframework.security.crypto.password.PasswordEncoder;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
@Service public class MeServiceImpl implements MeService{
 private static final Logger log=LoggerFactory.getLogger(MeServiceImpl.class);
 private final AppUserRepository users;private final PasswordEncoder passwords;private final AuthRateLimiter limiter;private final RefreshTokenService refreshes;private final Clock clock;private final Duration grace;
 public MeServiceImpl(AppUserRepository users,PasswordEncoder passwords,AuthRateLimiter limiter,RefreshTokenService refreshes,Clock clock,@Value("${app.account-deletion.grace-days:14}")long graceDays){this.users=users;this.passwords=passwords;this.limiter=limiter;this.refreshes=refreshes;this.clock=clock;this.grace=Duration.ofDays(graceDays);}
 public UserResponseDTO get(String id){return AuthServiceImpl.response(require(id));}
 @Transactional public UserResponseDTO update(String id,UpdateMeRequestDTO r){AppUser u=require(id);if(r.uiLanguage()!=null)u.setUiLanguage(r.uiLanguage());return AuthServiceImpl.response(users.save(u));}
 /**
  * Asks for the account to be deleted after the grace period (ADR 0009). Nothing is removed yet: the
  * request time is recorded and every session ends at once (the token version changes, so access
  * tokens stop working, and every refresh token is revoked). Signing in with the password before the
  * grace period ends cancels it; the purge job deletes the account, through the ON DELETE CASCADE
  * foreign keys, once it has ended. The password is required so that a stolen access token alone
  * cannot start a deletion, and attempts are rate limited per account.
  */
 @Override @Transactional public DeletionScheduledResponseDTO deleteAccount(String id,DeleteAccountRequestDTO r){
  limiter.checkDeleteAccount(id);
  AppUser u=require(id);
  if(!passwords.matches(r.password(),u.getPasswordHash()))throw new AuthException("INVALID_CREDENTIALS","Invalid password");
  Instant now=Instant.now(clock);
  u.setDeletionRequestedAt(now);
  u.setTokenVersion(u.getTokenVersion()+1);
  users.save(u);
  refreshes.revokeAll(id);
  // Internal id only; no email or other personal data is logged.
  log.info("account_deletion_requested userId={}",id);
  return new DeletionScheduledResponseDTO(now.plus(grace));
 }
 private AppUser require(String id){return users.findById(id).orElseThrow(()->new AuthException("UNAUTHENTICATED","Sign in required"));}}
