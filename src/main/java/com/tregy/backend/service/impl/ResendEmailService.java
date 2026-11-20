package com.tregy.backend.service.impl;
import com.tregy.backend.service.EmailService; import lombok.extern.slf4j.Slf4j; import org.springframework.beans.factory.annotation.Value; import org.springframework.scheduling.annotation.Async; import org.springframework.stereotype.Service; import org.springframework.web.client.RestClient;
import java.util.Map; import java.util.UUID;
@Service @Slf4j
public class ResendEmailService implements EmailService{
 private final RestClient client; private final String key; private final String from;
 public ResendEmailService(@Value("${app.email.resend-api-key:}")String key,@Value("${app.email.from:}")String from){var rf=new org.springframework.http.client.SimpleClientHttpRequestFactory();rf.setConnectTimeout(5000);rf.setReadTimeout(10000);this.client=RestClient.builder().requestFactory(rf).baseUrl("https://api.resend.com").build();this.key=key;this.from=from;}
 @Override @Async public void sendVerification(String to,String link){send(to,"Verify your Tregy email","Open this link to verify your email: "+link,"verification");}
 @Override @Async public void sendPasswordReset(String to,String link){send(to,"Reset your Tregy password","Open this link to reset your password: "+link,"password_reset");}
 private void send(String to,String subject,String text,String event){String requestId=UUID.randomUUID().toString();if(key.isBlank()||from.isBlank()){log.info("Email delivery suppressed event={} requestId={}",event,requestId);return;}try{client.post().uri("/emails").header("Authorization","Bearer "+key).body(Map.of("from",from,"to",new String[]{to},"subject",subject,"text",text)).retrieve().toBodilessEntity();log.info("Email accepted event={} requestId={}",event,requestId);}catch(org.springframework.web.client.RestClientException e){log.warn("Email delivery failed event={} requestId={} errorType={}",event,requestId,e.getClass().getSimpleName());}}
}
