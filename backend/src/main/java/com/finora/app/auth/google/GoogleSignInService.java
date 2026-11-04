package com.finora.app.auth.google;
import com.finora.app.auth.*;
import com.finora.app.gmail.security.TokenCipher;
import com.finora.app.shared.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.*;
@Service
public class GoogleSignInService {
 public record Start(String authorizationUrl,String browserCookie){@Override public String toString(){return "GoogleStart[REDACTED]";}}
 public record Result(String sessionToken,boolean linkRequired){@Override public String toString(){return "GoogleResult[REDACTED]";}}
 private final GoogleAuthFlowRepository flows;
 private final GoogleIdentityClient google;
 private final TokenCipher cipher;
 private final UserRepository users;
 private final BCryptPasswordEncoder passwords;
 private final AccountSessions sessions;
 public GoogleSignInService(GoogleAuthFlowRepository flows,GoogleIdentityClient google,TokenCipher cipher,UserRepository users,BCryptPasswordEncoder passwords,AccountSessions sessions){
  this.flows=flows;this.google=google;this.cipher=cipher;this.users=users;this.passwords=passwords;this.sessions=sessions;
 }
 @Transactional public Start start(){
  String state=AuthSecrets.random(),browser=AuthSecrets.random(),nonce=AuthSecrets.random(),pkce=AuthSecrets.random();
  String url=google.authorizationUrl(state,nonce,pkce);
  var f=new GoogleAuthFlow();f.stateHash=AuthSecrets.hash(state);f.browserHash=AuthSecrets.hash(browser);f.nonceHash=AuthSecrets.hash(nonce);f.encryptedVerifier=cipher.encrypt(pkce);f.expiresAt=Instant.now().plusSeconds(600);flows.save(f);
  return new Start(url,state+"."+browser);
 }
 private GoogleAuthFlow bound(String state,String cookie){
  if(state==null||cookie==null||!cookie.startsWith(state+"."))throw invalid();
  var f=flows.locked(AuthSecrets.hash(state)).orElseThrow(this::invalid);
  if(f.completedAt!=null||f.expiresAt.isBefore(Instant.now())||!AuthSecrets.matches(f.browserHash,cookie.substring(state.length()+1)))throw invalid();
  return f;
 }
 @Transactional(noRollbackFor=ApiException.class) public Result callback(String code,String state,String cookie,String error){
  var f=bound(state,cookie);
  if(f.consumedAt!=null)throw invalid();
  f.consumedAt=Instant.now();flows.saveAndFlush(f);
  if(error!=null){f.completedAt=Instant.now();throw new ApiException(400,"GOOGLE_CANCELLED","No se autorizó el acceso con Google.");}
  if(code==null||code.isBlank())throw invalid();
  var identity=google.identity(code,cipher.decrypt(f.encryptedVerifier),f.nonceHash);
  var known=users.findByGoogleSubject(identity.subject());
  if(known.isPresent()){f.completedAt=Instant.now();return new Result(sessions.issue(known.get().id),false);}
  var existing=users.findByEmailIgnoreCase(identity.email());
  if(existing.isPresent()){
   if(existing.get().googleSubject!=null)throw new ApiException(409,"GOOGLE_LINK_CONFLICT","La cuenta ya está vinculada a otra identidad de Google.");
   f.linkUserId=existing.get().id;f.googleSubject=identity.subject();f.googleEmail=identity.email();f.googleName=identity.name();
   return new Result(null,true);
  }
  var u=new User(identity.name(),identity.email(),passwords.encode(AuthSecrets.random()));
  u.googleSubject=identity.subject();u.googleLinkedAt=Instant.now();u.role="USER";users.saveAndFlush(u);
  f.completedAt=Instant.now();return new Result(sessions.issue(u.id),false);
 }
 @Transactional(noRollbackFor=ApiException.class) public Result link(String cookie,String password){
  if(cookie==null||!cookie.contains("."))throw invalid();
  var f=bound(cookie.substring(0,cookie.indexOf('.')),cookie);
  if(f.consumedAt==null||f.linkUserId==null||f.googleSubject==null||f.linkAttempts>=5)throw invalid();
  f.linkAttempts++;flows.saveAndFlush(f);
  var u=users.locked(f.linkUserId).orElseThrow(this::invalid);
  if(flows.recentLinkAttempts(u.id,Instant.now().minusSeconds(3000))>10)
   throw new ApiException(429,"GOOGLE_LINK_RATE_LIMIT","Se alcanzó el límite de intentos de vinculación. Intenta más tarde o contacta al operador.");
  if(password==null||password.length()>256||!passwords.matches(password,u.passwordHash))
   throw new ApiException(401,"GOOGLE_LINK_PROOF_REQUIRED","Verifica la contraseña anterior de Sarela para vincular tu cuenta. Máximo cinco intentos.");
  if(u.googleSubject!=null||!u.email.equalsIgnoreCase(f.googleEmail)||users.findByGoogleSubject(f.googleSubject).isPresent())
   throw new ApiException(409,"GOOGLE_LINK_CONFLICT","No se pudo vincular esta cuenta. Contacta al operador.");
  u.googleSubject=f.googleSubject;u.googleLinkedAt=Instant.now();users.saveAndFlush(u);f.completedAt=Instant.now();
  return new Result(sessions.issue(u.id),false);
 }
 private ApiException invalid(){return new ApiException(400,"GOOGLE_FLOW_INVALID","El acceso expiró o no corresponde a este navegador. Inicia nuevamente con Google.");}
}
