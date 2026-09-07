package com.finora.app.gmail.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name = "gmail_oauth_states", indexes = @Index(name = "idx_oauth_state_hash", columnList = "state_hash", unique = true))
public class GmailOAuthState {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "user_id", nullable = false) public Long userId;
    @Column(name = "state_hash", nullable = false, length = 64) public String stateHash;
    @Column(nullable = false) public Instant expiresAt;
    public Instant usedAt;
    public Instant createdAt = Instant.now();
    public GmailOAuthState() {}
}
