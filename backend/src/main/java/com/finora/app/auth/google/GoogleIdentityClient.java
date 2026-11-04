package com.finora.app.auth.google;
import com.fasterxml.jackson.databind.*;
import com.finora.app.shared.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
@Component
public class GoogleIdentityClient {
 private final String clientId,secret,redirectUri;
 private final GoogleIdTokenVerifier verifier;
 private final ObjectMapper json;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
 public GoogleIdentityClient(GoogleIdTokenVerifier verifier,ObjectMapper json,
   @Value("${app.gmail.client-id:}")String clientId,@Value("${app.gmail.client-secret:}")String secret,
   @Value("${app.auth.google-redirect-uri:http://localhost:8080/api/v1/auth/google/callback}")String redirectUri){
  this.verifier=verifier;this.json=json;this.clientId=clientId;this.secret=secret;this.redirectUri=redirectUri;
 }
 public boolean configured(){return !clientId.isBlank()&&!secret.isBlank();}
 public String authorizationUrl(String state,String nonce,String verifier){
  if(!configured())throw new ApiException(503,"GOOGLE_NOT_CONFIGURED","El acceso con Google todavía no está configurado.");
  return "https://accounts.google.com/o/oauth2/v2/auth?client_id="+enc(clientId)+"&redirect_uri="+enc(redirectUri)+"&response_type=code&scope=openid%20email%20profile&state="+enc(state)+"&nonce="+enc(nonce)+"&code_challenge_method=S256&code_challenge="+AuthSecrets.challenge(verifier)+"&prompt=select_account";
 }
 public GoogleIdTokenVerifier.Identity identity(String code,String pkce,String nonceHash){
  try{
   var form=Map.of("client_id",clientId,"client_secret",secret,"redirect_uri",redirectUri,"grant_type","authorization_code","code",code,"code_verifier",pkce);
   String body=form.entrySet().stream().map(e->enc(e.getKey())+"="+enc(e.getValue())).collect(java.util.stream.Collectors.joining("&"));
   var request=HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token")).timeout(Duration.ofSeconds(20)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build();
   var response=http.send(request,HttpResponse.BodyHandlers.ofString());
   if(response.statusCode()!=200)throw new ApiException(401,"GOOGLE_LOGIN_FAILED","Google no pudo completar el acceso. Vuelve a intentarlo.");
   return verifier.verify(json.readTree(response.body()).path("id_token").asText(),nonceHash);
  }catch(ApiException e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new ApiException(503,"GOOGLE_UNAVAILABLE","No pudimos conectar con Google.");}
   catch(Exception e){throw new ApiException(503,"GOOGLE_UNAVAILABLE","No pudimos conectar con Google.");}
 }
 private String enc(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
}
