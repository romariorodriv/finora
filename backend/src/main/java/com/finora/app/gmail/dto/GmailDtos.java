package com.finora.app.gmail.dto;

import java.time.Instant;

public final class GmailDtos {
    private GmailDtos() {}
    public record AuthorizationUrlResponse(String authorizationUrl, boolean configured) {}
    public record StatusResponse(boolean connected, boolean configured, String email, String status, Instant lastSyncAt) {}
    public record SyncResponse(int messagesFound, int messagesProcessed, int transactionsCreated, int duplicatesSkipped, int reviewRequired, int rejected, int failed) {}
}
