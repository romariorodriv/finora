package com.finora.app.auth.google;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.Optional;
@Service
public class AccountSessions {
 private final AccountSessionRepository sessions;
 public AccountSessions(AccountSessionRepository sessions){this.sessions=sessions;}
 @Transactional public String issue(Long userId) {
  String token=AuthSecrets.random();var s=new AccountSession();s.tokenHash=AuthSecrets.hash(token);s.userId=userId;s.expiresAt=Instant.now().plus(Duration.ofHours(8));sessions.save(s);return token;
 }
 public Optional<Long> userId(String token) {
  if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))return Optional.empty();
  return sessions.findById(AuthSecrets.hash(token)).filter(s->s.expiresAt.isAfter(Instant.now())).map(s->s.userId);
 }
 @Transactional public void revoke(String token){if(token!=null)sessions.deleteById(AuthSecrets.hash(token));}
}
