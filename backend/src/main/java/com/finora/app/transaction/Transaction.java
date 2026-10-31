package com.finora.app.transaction;
import jakarta.persistence.*; import java.math.BigDecimal; import java.time.*;
@Entity @Table(name="transactions",uniqueConstraints=@UniqueConstraint(columnNames={"user_id","external_id"}))
public class Transaction {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id; @Column(name="user_id",nullable=false) public Long userId;
 @Column(nullable=false) public String description; @Column(nullable=false,precision=12,scale=2) public BigDecimal amount;
 @Column(nullable=false) public LocalDate date; @Column(nullable=false) public String category; @Column(nullable=false) public String type="EXPENSE";
 @Column(name="macro_category") public String macroCategory;
 public String source="MANUAL"; @Column(name="external_id") public String externalId; public String merchant; public boolean recurring; public Instant createdAt=Instant.now();
 @Column(name="category_id") public Long categoryId;
 @Column(name="classification_source",nullable=false,length=30) public String classificationSource="LEGACY";
 @Column(name="classification_rule_id") public Long classificationRuleId;
 @Column(name="review_status",nullable=false,length=15) public String reviewStatus="PENDING";
 @Column(name="normalized_merchant",length=500) public String normalizedMerchant;
 @Column(name="original_description",columnDefinition="TEXT") public String originalDescription;
 @Column(nullable=false,length=3) public String currency="PEN";
 @Version public Long version;
 public Transaction(){}
}
