package com.finora.app.admin;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="admin_audit")
public class AdminAudit {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(name="actor_id") public Long actorId;
 @Column(name="actor_kind",nullable=false,length=30) public String actorKind="ADMIN";
 @Column(nullable=false,length=40) public String action;
 @Column(name="entity_type",nullable=false,length=30) public String entityType;
 @Column(name="entity_id",nullable=false,length=50) public String entityId;
 @Column(name="before_json",columnDefinition="TEXT") public String beforeJson;
 @Column(name="after_json",columnDefinition="TEXT") public String afterJson;
 @Column(name="batch_id",length=36) public String batchId;
 @Column(name="created_at",nullable=false) public Instant createdAt=Instant.now();
}
