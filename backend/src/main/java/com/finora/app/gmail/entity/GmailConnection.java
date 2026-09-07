package com.finora.app.gmail.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "gmail_connections", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "gmail_address"}))
public class GmailConnection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "user_id", nullable = false) public Long userId;
    @Column(name = "gmail_address", nullable = false) public String gmailAddress;
    @Column(name = "encrypted_access_token", length = 4096) public String encryptedAccessToken;
    @Column(name = "encrypted_refresh_token", length = 4096) public String encryptedRefreshToken;
    public Instant accessTokenExpiresAt;
    public Instant lastSyncAt;
    @Enumerated(EnumType.STRING) public Status status = Status.CONNECTED;
    public Instant createdAt = Instant.now();
    public Instant updatedAt = Instant.now();

    public enum Status { CONNECTED, REAUTHORIZATION_REQUIRED, DISCONNECTED, ERROR }
    public GmailConnection() {}
}
