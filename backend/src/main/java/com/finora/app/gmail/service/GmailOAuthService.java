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
    private final GmailOAuthStateRepository states; private final GmailConnectionRepository connections;
    private final GoogleGmailClient gmail; private final TokenCipher cipher;
    private final String clientId, redirectUri, frontendUrl;
    private final SecureRandom random=new SecureRandom();
    public GmailOAuthService(GmailOAuthStateRepository states,GmailConnectionRepository connections,GoogleGmailClient gmail,TokenCipher cipher,
        @Value("${app.gmail.client-id:}")String clientId,
        @Value("${app.gmail.redirect-uri:http://localhost:8080/api/integrations/gmail/callback}")String redirectUri,
        @Value("${app.frontend-url:http://localhost:8080}")String frontendUrl){this.states=states;this.connections=connections;this.gmail=gmail;this.cipher=cipher;this.clientId=clientId;this.redirectUri=redirectUri;this.frontendUrl=frontendUrl;}

    @Transactional
    public GmailDtos.AuthorizationUrlResponse authorizationUrl(Long userId){
        ensureConfigured(); byte[] bytes=new byte[32];random.nextBytes(bytes);String state=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        GmailOAuthState row=new GmailOAuthState();row.userId=userId;row.stateHash=sha256(state);row.expiresAt=Instant.now().plus(Duration.ofMinutes(10));states.save(row);
        String url="https://accounts.google.com/o/oauth2/v2/auth?client_id="+enc(clientId)+"&redirect_uri="+enc(redirectUri)+"&response_type=code&scope="+enc("https://www.googleapis.com/auth/gmail.readonly")+"&access_type=offline&prompt=consent&include_granted_scopes=true&state="+enc(state);
        return new GmailDtos.AuthorizationUrlResponse(url,true);
    }

    @Transactional
    public String callback(String code,String state,String error){
        if(error!=null)return frontendUrl+"/?gmail=cancelled";
        if(code==null||state==null)throw new ApiException(400,"GMAIL_OAUTH_INVALID","Google no devolvió una autorización válida");
        GmailOAuthState saved=states.findByStateHash(sha256(state)).orElseThrow(()->new ApiException(400,"GMAIL_STATE_INVALID","La autorización expiró o no es válida"));
        if(saved.usedAt!=null||saved.expiresAt.isBefore(Instant.now()))throw new ApiException(400,"GMAIL_STATE_EXPIRED","La autorización expiró. Inténtalo nuevamente");saved.usedAt=Instant.now();states.save(saved);
        GmailClient.Tokens tokens=gmail.exchangeCode(code);GmailClient.Profile profile=gmail.profile(tokens.accessToken());
        GmailConnection c=connections.findByUserIdAndGmailAddress(saved.userId,profile.emailAddress()).orElseGet(GmailConnection::new);
        c.userId=saved.userId;c.gmailAddress=profile.emailAddress();c.encryptedAccessToken=cipher.encrypt(tokens.accessToken());
        if(tokens.refreshToken()!=null)c.encryptedRefreshToken=cipher.encrypt(tokens.refreshToken());
        c.accessTokenExpiresAt=Instant.now().plusSeconds(tokens.expiresInSeconds());c.status=GmailConnection.Status.CONNECTED;c.updatedAt=Instant.now();connections.save(c);
        return frontendUrl+"/?gmail=connected";
    }

    public GmailDtos.StatusResponse status(Long userId){return connections.findFirstByUserIdAndStatusNotOrderByUpdatedAtDesc(userId,GmailConnection.Status.DISCONNECTED).map(c->new GmailDtos.StatusResponse(c.status==GmailConnection.Status.CONNECTED,true,c.gmailAddress,c.status.name(),c.lastSyncAt)).orElse(new GmailDtos.StatusResponse(false,gmail.configured(),null,gmail.configured()?"NOT_CONNECTED":"DEMO",null));}

    @Transactional
    public void disconnect(Long userId){GmailConnection c=active(userId);try{gmail.revoke(cipher.decrypt(c.encryptedAccessToken));}catch(Exception ignored){}c.encryptedAccessToken=null;c.encryptedRefreshToken=null;c.status=GmailConnection.Status.DISCONNECTED;c.updatedAt=Instant.now();connections.save(c);}
    public GmailConnection active(Long userId){return connections.findFirstByUserIdAndStatusNotOrderByUpdatedAtDesc(userId,GmailConnection.Status.DISCONNECTED).filter(c->c.status==GmailConnection.Status.CONNECTED).orElseThrow(()->new ApiException(404,"GMAIL_NOT_CONNECTED","Primero conecta una cuenta Gmail"));}
    public String validAccessToken(GmailConnection c){if(c.accessTokenExpiresAt!=null&&c.accessTokenExpiresAt.isAfter(Instant.now().plusSeconds(60)))return cipher.decrypt(c.encryptedAccessToken);String refresh=cipher.decrypt(c.encryptedRefreshToken);if(refresh==null)throw new ApiException(401,"GMAIL_RECONNECT_REQUIRED","Vuelve a conectar Gmail");GmailClient.Tokens t=gmail.refresh(refresh);c.encryptedAccessToken=cipher.encrypt(t.accessToken());c.accessTokenExpiresAt=Instant.now().plusSeconds(t.expiresInSeconds());c.updatedAt=Instant.now();connections.save(c);return t.accessToken();}
    public GmailConnection save(GmailConnection connection){return connections.save(connection);}
    public boolean configured(){return gmail.configured();}
    private void ensureConfigured(){if(!gmail.configured())throw new ApiException(503,"GMAIL_NOT_CONFIGURED","Configura GOOGLE_CLIENT_ID y GOOGLE_CLIENT_SECRET para conectar Gmail");}
    private String sha256(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
}
