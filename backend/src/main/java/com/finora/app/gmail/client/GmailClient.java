package com.finora.app.gmail.client;

import java.time.Instant;
import java.util.List;

public interface GmailClient {
    record Tokens(String accessToken, String refreshToken, long expiresInSeconds) {}
    record Profile(String emailAddress) {}
    record MessageRef(String id, String threadId) {}
    record Page(List<MessageRef> messages, String nextPageToken) {}
    record Message(String id, String threadId, String sender, String subject, String plainText, Instant receivedAt) {}
    Tokens exchangeCode(String code);
    Tokens refresh(String refreshToken);
    Profile profile(String accessToken);
    Page search(String accessToken, String query, String pageToken);
    Message getMessage(String accessToken, String messageId);
    void revoke(String token);
}
