package com.tregy.backend.service.impl;
import com.tregy.backend.exception.AuthException; import com.tregy.backend.model.RefreshToken; import com.tregy.backend.repository.RefreshTokenRepository; import com.tregy.backend.service.RefreshTokenService; import com.tregy.backend.service.support.*;
import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.time.*; import java.util.UUID;
@Service
public class RefreshTokenServiceImpl implements RefreshTokenService{
 private final RefreshTokenRepository repository; private final Clock clock; private final Duration lifetime; private final Duration grace;
 public RefreshTokenServiceImpl(RefreshTokenRepository repository,Clock clock,@Value("${app.jwt.refresh-expiration-ms}") long lifetime,@Value("${app.jwt.refresh-reuse-grace-ms:10000}") long grace){this.repository=repository;this.clock=clock;this.lifetime=Duration.ofMillis(lifetime);this.grace=Duration.ofMillis(grace);}
 @Override public IssuedToken issue(String userId,String deviceId){return create(userId,deviceId,UUID.randomUUID().toString(),Instant.now(clock)).issued();}
 @Override @Transactional(noRollbackFor=AuthException.class) public RefreshRotation rotate(String raw){
  if(raw==null||raw.isBlank())throw invalid(); Instant now=Instant.now(clock);
  RefreshToken current=repository.findForUpdate(TokenHashing.sha256Hex(raw)).orElseThrow(this::invalid);
  if(current.wasRotated()){if(current.getRevokedAt().plus(grace).isBefore(now))repository.revokeFamily(current.getFamilyId(),now);throw invalid();}
  if(current.getRevokedAt()!=null||!current.getExpiresAt().isAfter(now))throw invalid();
  Created next=create(current.getUserId(),current.getDeviceId(),current.getFamilyId(),now);current.setRevokedAt(now);current.setReplacedById(next.entity().getId());repository.save(current);
  return new RefreshRotation(current.getUserId(),current.getDeviceId(),next.issued());
 }
 @Override @Transactional public void revoke(String raw){if(raw==null||raw.isBlank())return;repository.findForUpdate(TokenHashing.sha256Hex(raw)).filter(t->t.getRevokedAt()==null).ifPresent(t->{t.setRevokedAt(Instant.now(clock));repository.save(t);});}
 @Override @Transactional public void revokeAll(String userId){repository.revokeUser(userId,Instant.now(clock));}
 private Created create(String userId,String deviceId,String family,Instant now){String raw=TokenHashing.generateRawToken();RefreshToken t=new RefreshToken();t.setUserId(userId);t.setDeviceId(deviceId);t.setFamilyId(family);t.setTokenHash(TokenHashing.sha256Hex(raw));t.setExpiresAt(now.plus(lifetime));t.setCreatedAt(now);t=repository.save(t);return new Created(t,new IssuedToken(raw,t.getExpiresAt()));}
 private AuthException invalid(){return new AuthException("INVALID_REFRESH_TOKEN","Session expired. Sign in again");}
 private record Created(RefreshToken entity,IssuedToken issued){}
}
