package com.finora.app.admin;
import jakarta.persistence.*;
@Entity @Table(name="classification_rules",uniqueConstraints=@UniqueConstraint(columnNames={"match_type","merchant_key","scope_key"}))
public class ClassificationRule {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(name="merchant_key",nullable=false,length=200) public String merchantKey;
 @Column(name="match_type",nullable=false,length=10) public String matchType="EXACT";
 @Column(name="scope_key",nullable=false,length=50) public String scopeKey="GLOBAL";
 @Column(name="user_id") public Long userId;
 @Column(name="category_id",nullable=false) public Long categoryId;
 @Column(nullable=false) public int priority=100;
 @Column(nullable=false) public boolean active=true;
 @Version public Long version;
}
