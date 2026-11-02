package com.finora.app.auth.google;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.junit.jupiter.api.*;
import com.finora.app.shared.error.ApiException;
import java.time.Instant;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;
class GoogleIdentityVerificationTest {
 static RSAKey key;GoogleIdTokenVerifier verifier;
 @BeforeAll static void keys()throws Exception{key=new RSAKeyGenerator(2048).generate();}
 @BeforeEach void setup()throws Exception{verifier=new GoogleIdTokenVerifier("client-test",NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build());}
 JWTClaimsSet.Builder claims(){return new JWTClaimsSet.Builder().issuer("https://accounts.google.com").audience("client-test").subject("stable-subject")
   .issueTime(Date.from(Instant.now().minusSeconds(10))).expirationTime(Date.from(Instant.now().plusSeconds(3600)))
   .claim("email","owner@example.test").claim("email_verified",true).claim("name","Cuenta QA").claim("nonce","browser-nonce");}
 String sign(JWTClaimsSet.Builder claims,RSAKey signing)throws Exception{
  var jwt=new SignedJWT(new JWSHeader(JWSAlgorithm.RS256),claims.build());jwt.sign(new RSASSASigner(signing));return jwt.serialize();
 }
 @Test void validSignedIdentityUsesStableSubject()throws Exception{
  var identity=verifier.verify(sign(claims(),key),AuthSecrets.hash("browser-nonce"));
  assertEquals("stable-subject",identity.subject());assertEquals("owner@example.test",identity.email());
 }
 @Test void invalidSignatureIsRejected()throws Exception{
  String token=sign(claims(),new RSAKeyGenerator(2048).generate());
  assertThrows(ApiException.class,()->verifier.verify(token,AuthSecrets.hash("browser-nonce")));
 }
 @Test void wrongAudienceIsRejected()throws Exception{
  String token=sign(claims().audience("another-client"),key);
  assertThrows(ApiException.class,()->verifier.verify(token,AuthSecrets.hash("browser-nonce")));
 }
 @Test void wrongIssuerIsRejected()throws Exception{
  String token=sign(claims().issuer("https://attacker.example"),key);
  assertThrows(ApiException.class,()->verifier.verify(token,AuthSecrets.hash("browser-nonce")));
 }
 @Test void expiredIdentityIsRejected()throws Exception{
  String token=sign(claims().expirationTime(Date.from(Instant.now().minusSeconds(120))),key);
  assertThrows(ApiException.class,()->verifier.verify(token,AuthSecrets.hash("browser-nonce")));
 }
 @Test void nonceMustBelongToBrowserFlow()throws Exception{
  String token=sign(claims(),key);
  assertThrows(ApiException.class,()->verifier.verify(token,AuthSecrets.hash("different-nonce")));
 }
 @Test void unverifiedEmailCannotCreateOrLinkAccount()throws Exception{
  String token=sign(claims().claim("email_verified",false),key);
  assertThrows(ApiException.class,()->verifier.verify(token,AuthSecrets.hash("browser-nonce")));
 }
 @Test void wrongAuthorizedPartyIsRejected()throws Exception{
  String token=sign(claims().claim("azp","another-client"),key);
  assertThrows(ApiException.class,()->verifier.verify(token,AuthSecrets.hash("browser-nonce")));
 }
}
