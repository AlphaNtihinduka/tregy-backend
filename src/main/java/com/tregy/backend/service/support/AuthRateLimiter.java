package com.tregy.backend.service.support;
import com.github.benmanes.caffeine.cache.*;import com.tregy.backend.exception.AuthException;import org.springframework.stereotype.Component;import java.time.Duration;import java.util.Locale;import java.util.concurrent.atomic.AtomicInteger;
@Component
public class AuthRateLimiter{
 private final Cache<String,AtomicInteger> shortWindow=Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(10)).maximumSize(10000).build();
 private final Cache<String,AtomicInteger> recoveryWindow=Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(1)).maximumSize(10000).build();
 public void checkLogin(String email){check(shortWindow,"login",email,10);}
 public void checkRegister(String email){check(shortWindow,"register",email,5);}
 // Password attempts when deleting an account, counted per account (the key is the user id).
 public void checkDeleteAccount(String userId){check(shortWindow,"delete-account",userId,5);}
 public void checkRecovery(String operation,String email){check(recoveryWindow,operation,email,3);}
 private void check(Cache<String,AtomicInteger> cache,String operation,String email,int limit){String normalized=email==null?"":email.strip().toLowerCase(Locale.ROOT);String key=operation+"|"+TokenHashing.sha256Hex(normalized);if(cache.get(key,k->new AtomicInteger()).incrementAndGet()>limit)throw new AuthException("RATE_LIMITED","Too many requests. Try again later");}
}
