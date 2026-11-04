package com.finora.app.auth.google;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="google_auth_flows")
public class GoogleAuthFlow {
 @Id @Column(length=64) public String stateHash;
 @Column(nullable=false,length=64) public String browserHash;
 @Column(nullable=false,length=64) public String nonceHash;
 @Column(nullable=false,length=4096) public String encryptedVerifier;
 @Column(nullable=false) public Instant expiresAt;
 public Instant consumedAt;
 public Instant completedAt;
 public Long linkUserId;
 @Column(length=255) public String googleSubject;
 @Column(length=255) public String googleEmail;
 @Column(length=255) public String googleName;
 public int linkAttempts;
 @Version public Long version;
}
