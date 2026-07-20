package com.aiwardrobe.studio.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_users")
public class AppUser {
  @Id private UUID id;
  @Column(nullable = false, unique = true, length = 40) private String username;
  @Column(name = "password_hash", nullable = false, length = 100) private String passwordHash;
  @Column(name = "created_at", nullable = false) private Instant createdAt;

  protected AppUser() {}
  public AppUser(UUID id, String username, String passwordHash, Instant createdAt) {
    this.id = id; this.username = username; this.passwordHash = passwordHash; this.createdAt = createdAt;
  }
  public UUID getId() { return id; }
  public String getUsername() { return username; }
  public String getPasswordHash() { return passwordHash; }
  public Instant getCreatedAt() { return createdAt; }
}
