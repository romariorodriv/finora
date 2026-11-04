package com.finora.app.gmail.service;

import com.finora.app.gmail.client.*;
import com.finora.app.gmail.dto.GmailDtos;
import com.finora.app.gmail.entity.*;
import com.finora.app.gmail.repository.*;
import com.finora.app.gmail.security.TokenCipher;
import com.finora.app.shared.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class GmailOAuthService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GmailOAuthService.class);
    private final GmailOAuthStateRepository states; private final GmailConnectionRepository connections;
    private final GoogleGmailClient gmail; private final TokenCipher cipher;
    private final String clientId, redirectUri, frontendUrl;
    private com.finora.app.privacy.DeletionRequestRepository deletions;
    @org.springframework.beans.factory.annotation.Autowired public void setDeletions(com.finora.app.privacy.DeletionRequestRepository deletions){this.deletions=deletions;}
    private com.finora.app.gmail.jobs.GmailJobStore jobs;
    @org.springframework.beans.factory.annotation.Autowired public void setJobs(com.finora.app.gmail.jobs.GmailJobStore jobs){this.jobs=jobs;}
    @Value("${app.gmail.initial-sync-days:90}") private int initialDays=90;
    private final SecureRandom random=new SecureRandom();
    public GmailOAuthService(GmailOAuthStateRepository states,GmailConnectionRepository connections,GoogleGmailClient gmail,TokenCipher cipher,
        @Value("${app.gmail.client-id:}")String clientId,
        @Value("${app.gmail.redirect-uri:http://localhost:8080/api/integrations/gmail/callback}")String redirectUri,
        @Value("${app.frontend-url:http://localhost:8080}")String frontendUrl){this.states=states;this.connections=connections;this.gmail=gmail;this.cipher=cipher;this.clientId=clientId;this.redirectUri=redirectUri;this.frontendUrl=frontendUrl;}

    @Transactional
    public GmailDtos.AuthorizationUrlResponse authorizationUrl(Long userId,String browser){
        ensureConfigured();ensureNotDeleting(userId); byte[] bytes=new byte[32];random.nextBytes(bytes);String state=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        GmailOAuthState row=new GmailOAuthState();row.userId=userId;row.stateHash=sha256(state);row.expiresAt=Instant.now().plus(Duration.ofMinutes(10));row.browserHash=sha256(browser);String verifier=com.finora.app.auth.google.AuthSecrets.random();row.encryptedVerifier=cipher.encrypt(verifier);states.save(row);
        String url="https://accounts.google.com/o/oauth2/v2/auth?client_id="+enc(clientId)+"&redirect_uri="+enc(redirectUri)+"&response_type=code&scope="+enc("https://www.googleapis.com/auth/gmail.readonly")+"&access_type=offline&prompt=consent&include_granted_scopes=true&state="+enc(state)+"&code_challenge_method=S256&code_challenge="+com.finora.app.auth.google.AuthSecrets.challenge(verifier);
        return new GmailDtos.AuthorizationUrlResponse(url,true);
    }

    @Transactional
    public String callback(String code,String state,String error,String browser){
        if(state==null||browser==null)throw new ApiException(400,"GMAIL_OAUTH_INVALID","Inicia la conexión desde este navegador.");
        GmailOAuthState saved=states.locked(sha256(state)).orElseThrow(()->new ApiException(400,"GMAIL_STATE_INVALID","La autorización expiró o no es válida"));
        if(!com.finora.app.auth.google.AuthSecrets.matches(saved.browserHash,browser)||saved.encryptedVerifier==null)
          throw new ApiException(400,"GMAIL_STATE_INVALID","La autorización no corresponde a este navegador.");
        if(saved.usedAt!=null||saved.expiresAt.isBefore(Instant.now()))throw new ApiException(400,"GMAIL_STATE_EXPIRED","La autorización expiró. Inténtalo nuevamente");saved.usedAt=Instant.now();states.save(saved);
        ensureNotDeleting(saved.userId);
        if(error!=null)return frontendUrl+"/?gmail=cancelled";
        if(code==null||code.isBlank())throw new ApiException(400,"GMAIL_OAUTH_INVALID","Google no devolvió una autorización válida");
        GmailClient.Tokens tokens;
        GmailClient.Profile profile;
        try{tokens=gmail.exchangeCode(code,cipher.decrypt(saved.encryptedVerifier));profile=gmail.profile(tokens.accessToken());}
        catch(GoogleGmailClient.GmailApiException e){throw new ApiException(502,"GMAIL_AUTH_FAILED","Google no pudo completar la autorización. Vuelve a conectar Gmail.");}
        GmailConnection c=connections.findByUserIdAndGmailAddress(saved.userId,profile.emailAddress()).orElseGet(GmailConnection::new);
        // One selected mailbox per account; keep old imported movements, retire old credentials.
        for(var old:connections.findByUserIdAndStatus(saved.userId,GmailConnection.Status.CONNECTED)){
          if(old.userId.equals(saved.userId)&&!java.util.Objects.equals(old.id,c.id)){
            old.status=GmailConnection.Status.DISCONNECTED;old.encryptedAccessToken=null;old.encryptedRefreshToken=null;old.updatedAt=Instant.now();connections.save(old);
          }
        }
        c.userId=saved.userId;c.gmailAddress=profile.emailAddress();c.encryptedAccessToken=cipher.encrypt(tokens.accessToken());
        if(tokens.refreshToken()!=null)c.encryptedRefreshToken=cipher.encrypt(tokens.refreshToken());
        c.accessTokenExpiresAt=Instant.now().plusSeconds(tokens.expiresInSeconds());c.status=GmailConnection.Status.CONNECTED;c.updatedAt=Instant.now();connections.saveAndFlush(c);
        if(jobs!=null)jobs.enqueue(saved.userId);
        return frontendUrl+"/?gmail=connected";
    }

    public GmailDtos.StatusResponse status(Long userId){return connections.findFirstByUserIdAndStatusNotOrderByUpdatedAtDesc(userId,GmailConnection.Status.DISCONNECTED).map(c->new GmailDtos.StatusResponse(c.status==GmailConnection.Status.CONNECTED,gmail.configured(),c.gmailAddress,c.status.name(),c.lastSyncAt,Math.max(1,Math.min(365,initialDays)))).orElse(new GmailDtos.StatusResponse(false,gmail.configured(),null,gmail.configured()?"NOT_CONNECTED":"DEMO",null,Math.max(1,Math.min(365,initialDays))));}

    @Transactional
    public void disconnect(Long userId){
        if(jobs!=null)jobs.cancel(userId);
        for(GmailConnection c:connections.findByUserId(userId)){
          if(c.status==GmailConnection.Status.DISCONNECTED&&c.encryptedAccessToken==null&&c.encryptedRefreshToken==null)continue;
          try{gmail.revoke(cipher.decrypt(c.encryptedRefreshToken!=null?c.encryptedRefreshToken:c.encryptedAccessToken));}catch(Exception ignored){}
          c.encryptedAccessToken=null;c.encryptedRefreshToken=null;c.status=GmailConnection.Status.DISCONNECTED;c.updatedAt=Instant.now();connections.save(c);
        }
    }

    public GmailConnection active(Long userId) {
        var connection = connections.findFirstByUserIdAndStatusNotOrderByUpdatedAtDesc(
            userId, GmailConnection.Status.DISCONNECTED)
            .orElseThrow(() -> new ApiException(404, "GMAIL_NOT_CONNECTED", "Primero conecta una cuenta Gmail"));
        if (connection.status == GmailConnection.Status.REAUTHORIZATION_REQUIRED)
            throw new ApiException(401, "GMAIL_RECONNECT_REQUIRED", "La autorización de Gmail venció o fue revocada. Vuelve a conectar Gmail.");
        if (connection.status != GmailConnection.Status.CONNECTED)
            throw new ApiException(404, "GMAIL_NOT_CONNECTED", "Primero conecta una cuenta Gmail");
        return connection;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public String validAccessToken(GmailConnection c) {
        if (c.accessTokenExpiresAt != null && c.accessTokenExpiresAt.isAfter(Instant.now().plusSeconds(60)))
            return cipher.decrypt(c.encryptedAccessToken);
        String refresh = cipher.decrypt(c.encryptedRefreshToken);
        if (refresh == null || refresh.isBlank()) throw reconnectRequired(c);
        GmailClient.Tokens tokens;
        try {
            tokens = gmail.refresh(refresh);
        } catch (GoogleGmailClient.GmailApiException e) {
            log.warn("Gmail token refresh rejected: HTTP {}, OAuth code {}", e.status,
                e.oauthError == null ? "unrecognized" : e.oauthError);
            if ("invalid_grant".equals(e.oauthError)) throw reconnectRequired(c);
            if (Set.of("invalid_client", "unauthorized_client", "deleted_client").contains(
                    e.oauthError == null ? "" : e.oauthError)) {
                throw new ApiException(503, "GMAIL_OAUTH_CONFIGURATION_ERROR",
                    "La configuración de conexión con Google no es válida. Contacta al administrador de Sarela.");
            }
            if (e.status == 429 || e.status >= 500) {
                throw new ApiException(503, "GMAIL_TEMPORARILY_UNAVAILABLE",
                    "Google no está disponible en este momento. Intenta nuevamente más tarde.");
            }
            throw new ApiException(502, "GMAIL_OAUTH_REQUEST_FAILED",
                "Google rechazó la renovación de acceso. El administrador debe revisar la configuración de Gmail.");
        }
        c.encryptedAccessToken = cipher.encrypt(tokens.accessToken());
        if(tokens.refreshToken()!=null&&!tokens.refreshToken().equals(refresh))c.encryptedRefreshToken=cipher.encrypt(tokens.refreshToken());
        c.accessTokenExpiresAt = Instant.now().plusSeconds(tokens.expiresInSeconds());
        c.updatedAt = Instant.now();
        connections.save(c);
        return tokens.accessToken();
    }

    private ApiException reconnectRequired(GmailConnection c) {
        c.status = GmailConnection.Status.REAUTHORIZATION_REQUIRED;
        c.updatedAt = Instant.now();
        connections.save(c);
        return new ApiException(401, "GMAIL_RECONNECT_REQUIRED",
            "La autorización de Gmail venció o fue revocada. Vuelve a conectar Gmail.");
    }
    @Transactional public void markReauthorization(Long userId,Long connectionId){
      connections.findById(connectionId).filter(c->c.userId.equals(userId)).ifPresent(c->{
       c.status=GmailConnection.Status.REAUTHORIZATION_REQUIRED;c.updatedAt=Instant.now();connections.save(c);
      });
    }
    public GmailConnection save(GmailConnection connection){return connections.save(connection);}
    public boolean configured(){return gmail.configured();}
    private void ensureNotDeleting(Long userId){
      if(deletions!=null&&deletions.findById(userId).filter(d->"PENDING".equals(d.status)).isPresent())
        throw new ApiException(409,"DELETION_PENDING","Hay una solicitud de eliminación pendiente. Contacta al operador.");
    }
    private void ensureConfigured(){if(!gmail.configured())throw new ApiException(503,"GMAIL_NOT_CONFIGURED","Configura GOOGLE_CLIENT_ID y GOOGLE_CLIENT_SECRET para conectar Gmail");}
    private String sha256(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
}
