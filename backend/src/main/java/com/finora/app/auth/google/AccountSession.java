package com.finora.app.auth.google;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="account_sessions",indexes=@Index(name="idx_account_sessions_expiry",columnList="expiresAt"))
public class AccountSession {
 @Id @Column(length=64) public String tokenHash;
 @Column(nullable=false) public Long userId;
 @Column(nullable=false) public Instant expiresAt;
 @Column(nullable=false) public Instant createdAt=Instant.now();
}
