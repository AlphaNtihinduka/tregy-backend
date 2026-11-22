package com.tregy.backend.service.impl;
import com.tregy.backend.dto.request.DeleteAccountRequestDTO;import com.tregy.backend.dto.request.UpdateMeRequestDTO;import com.tregy.backend.dto.response.UserResponseDTO;import com.tregy.backend.exception.AuthException;import com.tregy.backend.model.AppUser;import com.tregy.backend.repository.AppUserRepository;import com.tregy.backend.service.MeService;import com.tregy.backend.service.support.AuthRateLimiter;import org.slf4j.Logger;import org.slf4j.LoggerFactory;import org.springframework.security.crypto.password.PasswordEncoder;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
@Service public class MeServiceImpl implements MeService{
 private static final Logger log=LoggerFactory.getLogger(MeServiceImpl.class);
 private final AppUserRepository users;private final PasswordEncoder passwords;private final AuthRateLimiter limiter;
 public MeServiceImpl(AppUserRepository users,PasswordEncoder passwords,AuthRateLimiter limiter){this.users=users;this.passwords=passwords;this.limiter=limiter;}
 public UserResponseDTO get(String id){return AuthServiceImpl.response(require(id));}
 @Transactional public UserResponseDTO update(String id,UpdateMeRequestDTO r){AppUser u=require(id);if(r.uiLanguage()!=null)u.setUiLanguage(r.uiLanguage());return AuthServiceImpl.response(users.save(u));}
 /**
  * Deletes the account and, through the ON DELETE CASCADE foreign keys, every row that belongs to it
  * (devices, tokens, profile, themes, schedule state, sync events). Nothing about the user is kept:
  * no tombstone, no copy of the email or its hash (ADR 0009). The password is required so that a
  * stolen access token alone cannot erase an account, and attempts are rate limited per account.
  */
 @Override @Transactional public void deleteAccount(String id,DeleteAccountRequestDTO r){
  limiter.checkDeleteAccount(id);
  AppUser u=require(id);
  if(!passwords.matches(r.password(),u.getPasswordHash()))throw new AuthException("INVALID_CREDENTIALS","Invalid password");
  users.delete(u);
  users.flush();
  // The id no longer points at anything; no email or other personal data is logged.
  log.info("account_deleted userId={}",id);
 }
 private AppUser require(String id){return users.findById(id).orElseThrow(()->new AuthException("UNAUTHENTICATED","Sign in required"));}}
