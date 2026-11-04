package com.finora.app.privacy;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="data_deletion_requests")
public class DeletionRequest {
 @Id public Long userId;
 @Column(nullable=false,length=20) public String status="PENDING";
 @Column(nullable=false) public Instant requestedAt=Instant.now();
 public Instant completedAt;
}
