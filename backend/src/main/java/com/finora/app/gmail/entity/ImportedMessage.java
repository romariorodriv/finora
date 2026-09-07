package com.finora.app.gmail.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "imported_messages", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "external_message_id"}))
public class ImportedMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "user_id", nullable = false) public Long userId;
    @Column(name = "gmail_connection_id", nullable = false) public Long gmailConnectionId;
    @Column(name = "external_message_id", nullable = false) public String externalMessageId;
    public String threadId;
    public String sender;
    public String subject;
    public Instant receivedAt;
    @Column(length = 64) public String contentHash;
    @Enumerated(EnumType.STRING) public Status processingStatus = Status.RECEIVED;
    public String parserName;
    public String parserVersion;
    public Double confidence;
    public String errorCode;
    public Long createdTransactionId;
    public Instant createdAt = Instant.now();
    public Instant updatedAt = Instant.now();

    public enum Status { RECEIVED, PROCESSING, IMPORTED, REVIEW_REQUIRED, REJECTED, FAILED }
    public ImportedMessage() {}
}
