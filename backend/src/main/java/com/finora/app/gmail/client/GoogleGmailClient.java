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
    public GoogleGmailClient(ObjectMapper json,
        @Value("${app.gmail.client-id:}") String clientId,
        @Value("${app.gmail.client-secret:}") String clientSecret,
        @Value("${app.gmail.redirect-uri:http://localhost:8080/api/integrations/gmail/callback}") String redirectUri) {
        this.json=json; this.clientId=clientId; this.clientSecret=clientSecret; this.redirectUri=redirectUri;
        this.http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }
    public boolean configured(){return !clientId.isBlank()&&!clientSecret.isBlank();}
    public Tokens exchangeCode(String code){return token(Map.of("code",code,"client_id",clientId,"client_secret",clientSecret,"redirect_uri",redirectUri,"grant_type","authorization_code"));}
    public Tokens refresh(String refreshToken){Tokens t=token(Map.of("refresh_token",refreshToken,"client_id",clientId,"client_secret",clientSecret,"grant_type","refresh_token"));return new Tokens(t.accessToken(),refreshToken,t.expiresInSeconds());}
    private Tokens token(Map<String,String> form){JsonNode n=postForm("https://oauth2.googleapis.com/token",form);return new Tokens(required(n,"access_token"),n.path("refresh_token").asText(null),n.path("expires_in").asLong(3600));}
    public Profile profile(String accessToken){JsonNode n=get("https://gmail.googleapis.com/gmail/v1/users/me/profile",accessToken);return new Profile(required(n,"emailAddress"));}
    public Page search(String accessToken,String query,String pageToken){String url="https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=100&q="+enc(query);if(pageToken!=null&&!pageToken.isBlank())url+="&pageToken="+enc(pageToken);JsonNode n=get(url,accessToken);List<MessageRef> list=new ArrayList<>();n.path("messages").forEach(m->list.add(new MessageRef(m.path("id").asText(),m.path("threadId").asText())));return new Page(list,n.path("nextPageToken").asText(null));}
    public Message getMessage(String accessToken,String id){JsonNode n=get("https://gmail.googleapis.com/gmail/v1/users/me/messages/"+enc(id)+"?format=full",accessToken);JsonNode payload=n.path("payload");return new Message(id,n.path("threadId").asText(),header(payload,"From"),header(payload,"Subject"),extractText(payload),Instant.ofEpochMilli(n.path("internalDate").asLong()));}
    public void revoke(String token){if(token==null||token.isBlank())return;postForm("https://oauth2.googleapis.com/revoke",Map.of("token",token));}
    private String extractText(JsonNode part){String mime=part.path("mimeType").asText();String data=part.path("body").path("data").asText();if((mime.equals("text/plain")||mime.equals("text/html"))&&!data.isBlank()){String s=new String(Base64.getUrlDecoder().decode(data),StandardCharsets.UTF_8);return mime.equals("text/html")?s.replaceAll("(?s)<[^>]*>"," ").replaceAll("\\s+"," ").trim():s;}for(JsonNode child:part.path("parts")){String s=extractText(child);if(!s.isBlank())return s;}return "";}
    private String header(JsonNode payload,String name){for(JsonNode h:payload.path("headers"))if(name.equalsIgnoreCase(h.path("name").asText()))return h.path("value").asText();return "";}
    private JsonNode get(String url,String token){return send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("Authorization","Bearer "+token).GET().build());}
    private JsonNode postForm(String url,Map<String,String> form){String body=form.entrySet().stream().map(e->enc(e.getKey())+"="+enc(e.getValue())).reduce((a,b)->a+"&"+b).orElse("");return send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build());}
    private JsonNode send(HttpRequest req){try{HttpResponse<String> r=http.send(req,HttpResponse.BodyHandlers.ofString());if(r.statusCode()<200||r.statusCode()>299)throw new GmailApiException(r.statusCode(),"Google respondió HTTP "+r.statusCode());return r.body().isBlank()?json.createObjectNode():json.readTree(r.body());}catch(GmailApiException e){throw e;}catch(Exception e){throw new GmailApiException(503,"No se pudo comunicar con Google");}}
    private String required(JsonNode n,String key){String v=n.path(key).asText();if(v.isBlank())throw new GmailApiException(502,"Google no devolvió "+key);return v;}
    private String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
    public static class GmailApiException extends RuntimeException{public final int status;public GmailApiException(int status,String message){super(message);this.status=status;}}
}
