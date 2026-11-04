package com.finora.app.auth.google;
import org.springframework.beans.factory.annotation.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import com.finora.app.shared.error.ApiException;
import java.time.Instant;
@Component
public class GoogleIdTokenVerifier {
 public record Identity(String subject,String email,String name) {}
 private final JwtDecoder decoder;
 private final String clientId;
 @Autowired public GoogleIdTokenVerifier(@Value("${app.gmail.client-id:}")String clientId){
  this(clientId,NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build());
 }
 GoogleIdTokenVerifier(String clientId,JwtDecoder decoder){this.clientId=clientId;this.decoder=decoder;}
 public Identity verify(String encoded,String nonceHash){
  try {
   Jwt jwt=decoder.decode(encoded);
   String issuer=jwt.getClaimAsString("iss"), subject=jwt.getSubject(), email=jwt.getClaimAsString("email");
   if(!("https://accounts.google.com".equals(issuer)||"accounts.google.com".equals(issuer))
     ||clientId.isBlank()||!jwt.getAudience().contains(clientId)
     ||(jwt.hasClaim("azp")&&!clientId.equals(jwt.getClaimAsString("azp")))
     ||jwt.getExpiresAt()==null||jwt.getExpiresAt().isBefore(Instant.now())
     ||jwt.getIssuedAt()==null||jwt.getIssuedAt().isAfter(Instant.now().plusSeconds(60))
     ||!Boolean.TRUE.equals(jwt.getClaim("email_verified"))
     ||subject==null||subject.isBlank()||subject.length()>255
     ||email==null||email.length()>254||!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
     ||!AuthSecrets.matches(nonceHash,jwt.getClaimAsString("nonce")))throw new IllegalArgumentException();
   String name=jwt.getClaimAsString("name");
   return new Identity(subject,email.toLowerCase(java.util.Locale.ROOT),name==null||name.isBlank()?"Tu cuenta":name.substring(0,Math.min(255,name.length())));
  }catch(Exception e){throw new ApiException(401,"GOOGLE_IDENTITY_INVALID","No pudimos verificar tu identidad con Google. Inicia el acceso nuevamente.");}
 }
}
