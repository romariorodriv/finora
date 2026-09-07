package com.finora.app.transaction;
import jakarta.persistence.*; import java.math.BigDecimal; import java.time.*;
@Entity @Table(name="transactions",uniqueConstraints=@UniqueConstraint(columnNames={"user_id","external_id"}))
public class Transaction {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id; @Column(name="user_id",nullable=false) public Long userId;
 @Column(nullable=false) public String description; @Column(nullable=false,precision=12,scale=2) public BigDecimal amount;
 @Column(nullable=false) public LocalDate date; @Column(nullable=false) public String category; @Column(nullable=false) public String type="EXPENSE";
 public String source="MANUAL"; @Column(name="external_id") public String externalId; public String merchant; public boolean recurring; public Instant createdAt=Instant.now();
 public Transaction(){}
}
