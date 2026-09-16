package com.finora.app.feedback;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_feedback")
public class UserFeedback {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  @Column(name = "user_id", nullable = false) public Long userId;
  @Column(nullable = false) public int rating;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20, columnDefinition = "varchar(20)") public UnderstoodSpending understoodSpending;
  @Column(length = 1000) public String liked;
  @Column(length = 1000) public String improvement;
  @Column(length = 1000) public String wantedFeature;
  @Column(nullable = false) public Instant createdAt = Instant.now();

  public enum UnderstoodSpending { YES, PARTIALLY, NO }
  public UserFeedback() {}
}
