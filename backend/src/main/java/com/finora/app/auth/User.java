package com.finora.app.auth;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="app_users")
public class User {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @Column(nullable=false) public String name;
  @Column(nullable=false, unique=true) public String email;
  @Column(nullable=false) public String passwordHash;
  @Column(nullable=false) public Instant createdAt = Instant.now();
  protected User() {}
  public User(String name,String email,String passwordHash){this.name=name;this.email=email;this.passwordHash=passwordHash;}
}
