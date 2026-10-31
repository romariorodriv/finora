package com.finora.app.admin;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="classification_batches")
public class ClassificationBatch {
 @Id @Column(length=36) public String id;
 @Column(name="actor_id",nullable=false) public Long actorId;
 @Column(nullable=false,length=30) public String kind;
 @Column(nullable=false,length=15) public String status="PREVIEW";
 @Column(name="rule_id") public Long ruleId;
 @Column(name="rule_version") public Long ruleVersion;
 @Column(name="target_category_id",nullable=false) public Long targetCategoryId;
 @Column(name="target_category_version") public Long targetCategoryVersion;
 @Column(name="filter_json",columnDefinition="TEXT",nullable=false) public String filterJson;
 @Column(name="created_at",nullable=false) public Instant createdAt=Instant.now();
 @Column(name="expires_at",nullable=false) public Instant expiresAt=Instant.now().plusSeconds(900);
 @Column(name="applied_at") public Instant appliedAt;
 @Column(name="reverted_at") public Instant revertedAt;
 @Column(name="changed_count",nullable=false) public int changedCount;
 @Column(name="skipped_manual",nullable=false) public int skippedManual;
 @Version public Long version;
}
