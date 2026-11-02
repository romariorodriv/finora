package com.finora.app.gmail.jobs;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="gmail_sync_jobs")
public class GmailSyncJob {
 @Id public Long userId;
 @Column(nullable=false,length=36) public String runId;
 @Column(nullable=false) public Long connectionId;
 @Column(nullable=false,length=20) public String status="PENDING";
 @Column(nullable=false) public Instant periodFrom;
 @Column(nullable=false) public Instant periodUntil;
 @Column(columnDefinition="TEXT") public String nextPageToken;
 @Column(nullable=false) public Instant queuedAt=Instant.now();
 public Instant startedAt,completedAt;
 @Column(nullable=false) public Instant updatedAt=Instant.now();
 public Instant nextAttemptAt,leaseUntil;
 @Column(length=36) public String leaseToken;
 @Column(length=80) public String errorCode;
 public int retryCount,pagesProcessed;
 public boolean initialImport;
 @Version public Long version;
}
