package com.tregy.backend.unittests.service.support;
import com.tregy.backend.exception.AuthException;import com.tregy.backend.service.support.AuthRateLimiter;import org.junit.jupiter.api.Test;import static org.assertj.core.api.Assertions.*;
class AuthRateLimiterUnitTest{
 @Test void shouldNormalizeEmailBeforeCountingLoginAttempts(){AuthRateLimiter limiter=new AuthRateLimiter();for(int i=0;i<10;i++)limiter.checkLogin(i%2==0?" Person@Example.com ":"person@example.com");assertThatThrownBy(()->limiter.checkLogin("PERSON@example.com")).isInstanceOf(AuthException.class).hasMessage("Too many requests. Try again later");}
 @Test void shouldKeepRecoveryOperationsIndependent(){AuthRateLimiter limiter=new AuthRateLimiter();for(int i=0;i<3;i++){limiter.checkRecovery("forgot","person@example.com");limiter.checkRecovery("resend","person@example.com");}assertThatThrownBy(()->limiter.checkRecovery("forgot","person@example.com")).isInstanceOf(AuthException.class);assertThatThrownBy(()->limiter.checkRecovery("resend","person@example.com")).isInstanceOf(AuthException.class);}
}
