package com.finora.app.gmail.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Component
public class GoogleGmailClient implements GmailClient {
    private final ObjectMapper json; private final HttpClient http;
    private final String clientId, clientSecret, redirectUri;
    @Value("${app.gmail.page-size:50}") private int pageSize=50;
    @org.springframework.beans.factory.annotation.Autowired
    public GoogleGmailClient(ObjectMapper json,
        @Value("${app.gmail.client-id:}") String clientId,
        @Value("${app.gmail.client-secret:}") String clientSecret,
        @Value("${app.gmail.redirect-uri:http://localhost:8080/api/integrations/gmail/callback}") String redirectUri) {
        this(json, clientId, clientSecret, redirectUri,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }
    GoogleGmailClient(ObjectMapper json, String clientId, String clientSecret, String redirectUri, HttpClient http) {
        this.json=json; this.clientId=clientId; this.clientSecret=clientSecret; this.redirectUri=redirectUri;
        this.http=http;
    }
    public boolean configured(){return !clientId.isBlank()&&!clientSecret.isBlank();}
    public Tokens exchangeCode(String code){return token(Map.of("code",code,"client_id",clientId,"client_secret",clientSecret,"redirect_uri",redirectUri,"grant_type","authorization_code"));}
    public Tokens exchangeCode(String code,String verifier){return token(Map.of("code",code,"client_id",clientId,"client_secret",clientSecret,"redirect_uri",redirectUri,"grant_type","authorization_code","code_verifier",verifier));}
    public Tokens refresh(String refreshToken){Tokens t=token(Map.of("refresh_token",refreshToken,"client_id",clientId,"client_secret",clientSecret,"grant_type","refresh_token"));return new Tokens(t.accessToken(),t.refreshToken()==null?refreshToken:t.refreshToken(),t.expiresInSeconds());}
    private Tokens token(Map<String,String> form){JsonNode n=postForm("https://oauth2.googleapis.com/token",form);return new Tokens(required(n,"access_token"),n.path("refresh_token").asText(null),n.path("expires_in").asLong(3600));}
    public Profile profile(String accessToken){JsonNode n=get("https://gmail.googleapis.com/gmail/v1/users/me/profile",accessToken);return new Profile(required(n,"emailAddress"));}
    public Page search(String accessToken,String query,String pageToken){String url="https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults="+Math.max(1,Math.min(100,pageSize))+"&q="+enc(query);if(pageToken!=null&&!pageToken.isBlank())url+="&pageToken="+enc(pageToken);JsonNode n=get(url,accessToken);List<MessageRef> list=new ArrayList<>();n.path("messages").forEach(m->list.add(new MessageRef(m.path("id").asText(),m.path("threadId").asText())));return new Page(list,n.path("nextPageToken").asText(null));}
    public Message getMessage(String accessToken,String id){JsonNode n=get("https://gmail.googleapis.com/gmail/v1/users/me/messages/"+enc(id)+"?format=full",accessToken);JsonNode payload=n.path("payload");return new Message(id,n.path("threadId").asText(),header(payload,"From"),header(payload,"Subject"),extractText(payload),receivedAt(n));}
    private Instant receivedAt(JsonNode message){
        try{long timestamp=Long.parseLong(message.path("internalDate").asText());return timestamp>0?Instant.ofEpochMilli(timestamp):null;}catch(Exception ignored){return null;}
    }
    public void revoke(String token){if(token==null||token.isBlank())return;postForm("https://oauth2.googleapis.com/revoke",Map.of("token",token));}
    private String extractText(JsonNode part){String mime=part.path("mimeType").asText();String data=part.path("body").path("data").asText();if((mime.equals("text/plain")||mime.equals("text/html"))&&!data.isBlank()){String s=new String(Base64.getUrlDecoder().decode(data),StandardCharsets.UTF_8);return mime.equals("text/html")?s.replaceAll("(?s)<[^>]*>"," ").replaceAll("\\s+"," ").trim():s;}for(JsonNode child:part.path("parts")){String s=extractText(child);if(!s.isBlank())return s;}return "";}
    private String header(JsonNode payload,String name){for(JsonNode h:payload.path("headers"))if(name.equalsIgnoreCase(h.path("name").asText()))return h.path("value").asText();return "";}
    private JsonNode get(String url,String token){return send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("Authorization","Bearer "+token).GET().build());}
    private JsonNode postForm(String url,Map<String,String> form){String body=form.entrySet().stream().map(e->enc(e.getKey())+"="+enc(e.getValue())).reduce((a,b)->a+"&"+b).orElse("");return send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build());}
    private JsonNode send(HttpRequest req){try{HttpResponse<String> r=http.send(req,HttpResponse.BodyHandlers.ofString());if(r.statusCode()<200||r.statusCode()>299)throw new GmailApiException(r.statusCode(),"Google respondió HTTP "+r.statusCode(),oauthError(r.body()),gmailReason(r.body()));return r.body().isBlank()?json.createObjectNode():json.readTree(r.body());}catch(GmailApiException e){throw e;}catch(Exception e){throw new GmailApiException(503,"No se pudo comunicar con Google");}}
    private String gmailReason(String body) {
        try { return json.readTree(body).path("error").path("errors").path(0).path("reason").asText(null); }
        catch (Exception ignored) { return null; }
    }
    private String oauthError(String body) {
        try { return json.readTree(body).path("error").asText(null); }
        catch (Exception ignored) { return null; }
    }
    private String required(JsonNode n,String key){String v=n.path(key).asText();if(v.isBlank())throw new GmailApiException(502,"Google no devolvió "+key);return v;}
    private String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
    public static class GmailApiException extends RuntimeException {
        private static final Set<String> SAFE_ERRORS = Set.of(
            "invalid_grant", "invalid_client", "unauthorized_client", "deleted_client",
            "invalid_request", "invalid_scope", "unsupported_grant_type",
            "access_denied", "admin_policy_enforced", "temporarily_unavailable", "server_error");
        public final int status;
        public final String oauthError;
        public final String googleReason;
        public GmailApiException(int status, String message) { this(status, message, null); }
        public GmailApiException(int status, String message, String error) { this(status,message,error,null); }
        public GmailApiException(int status, String message, String error, String reason) {
            super(message);
            this.googleReason = Set.of("rateLimitExceeded","userRateLimitExceeded","quotaExceeded","domainPolicy","insufficientPermissions").contains(reason==null?"":reason)?reason:null;
            this.status = status;
            this.oauthError = SAFE_ERRORS.contains(error == null ? "" : error) ? error : null;
        }
    }
}
