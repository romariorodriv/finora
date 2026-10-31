package com.finora.app.admin;
import jakarta.persistence.*;
@Entity @Table(name="classification_changes",uniqueConstraints=@UniqueConstraint(columnNames={"batch_id","transaction_id"}))
public class ClassificationChange {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(name="batch_id",nullable=false,length=36) public String batchId;
 @Column(name="transaction_id",nullable=false) public Long transactionId;
 @Column(name="before_json",nullable=false,columnDefinition="TEXT") public String beforeJson;
 @Column(name="after_json",nullable=false,columnDefinition="TEXT") public String afterJson;
}
