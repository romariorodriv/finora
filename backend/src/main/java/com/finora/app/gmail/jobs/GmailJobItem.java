package com.finora.app.gmail.jobs;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="gmail_job_items",uniqueConstraints=@UniqueConstraint(name="uq_gmail_job_item",columnNames={"run_id","message_id"}))
public class GmailJobItem {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(name="run_id",nullable=false,length=36) public String runId;
 @Column(nullable=false) public Long userId;
 @Column(name="message_id",nullable=false,length=255) public String messageId;
 @Column(nullable=false,length=20) public String outcome;
 @Column(length=80) public String errorCode;
 @Column(nullable=false) public Instant createdAt=Instant.now();
}
