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
  @Column(unique = true, length = 254) private String email;
  @Column(name = "password_hash", length = 100) private String passwordHash;
  @Column(name = "email_verified", nullable = false) private boolean emailVerified;
  @Column(name = "verification_token_hash", length = 64) private String verificationTokenHash;
  @Column(name = "verification_expires_at") private Instant verificationExpiresAt;
  @Column(name = "password_reset_token_hash", length = 64) private String passwordResetTokenHash;
  @Column(name = "password_reset_expires_at") private Instant passwordResetExpiresAt;
  @Column(name = "oauth_provider", length = 32) private String oauthProvider;
  @Column(name = "oauth_subject", length = 255) private String oauthSubject;
  @Column(name = "created_at", nullable = false) private Instant createdAt;

  protected AppUser() {}
  public AppUser(UUID id, String username, String email, String passwordHash, Instant createdAt) {
    this.id = id; this.username = username; this.email = email; this.passwordHash = passwordHash; this.createdAt = createdAt;
  }
  public static AppUser googleUser(UUID id, String username, String email, String subject, String passwordHash, Instant createdAt) {
    AppUser user = new AppUser(id, username, email, passwordHash, createdAt);
    user.emailVerified = true;
    user.oauthProvider = "google";
    user.oauthSubject = subject;
    return user;
  }
  public UUID getId() { return id; }
  public String getUsername() { return username; }
  public String getEmail() { return email; }
  public String getPasswordHash() { return passwordHash; }
  public boolean isEmailVerified() { return emailVerified; }
  public String getVerificationTokenHash() { return verificationTokenHash; }
  public Instant getVerificationExpiresAt() { return verificationExpiresAt; }
  public String getPasswordResetTokenHash() { return passwordResetTokenHash; }
  public Instant getPasswordResetExpiresAt() { return passwordResetExpiresAt; }
  public String getOauthProvider() { return oauthProvider; }
  public String getOauthSubject() { return oauthSubject; }
  public Instant getCreatedAt() { return createdAt; }
  public void setVerification(String tokenHash, Instant expiresAt) {
    this.emailVerified = false;
    this.verificationTokenHash = tokenHash;
    this.verificationExpiresAt = expiresAt;
  }
  public void markEmailVerified() {
    this.emailVerified = true;
    this.verificationTokenHash = null;
    this.verificationExpiresAt = null;
  }
  public void linkGoogle(String subject) {
    this.oauthProvider = "google";
    this.oauthSubject = subject;
    this.emailVerified = true;
  }
  public void completeGoogleProfile(String username, String passwordHash) {
    this.username = username;
    this.passwordHash = passwordHash;
    this.emailVerified = true;
  }
  public void setPasswordReset(String tokenHash, Instant expiresAt) {
    this.passwordResetTokenHash = tokenHash;
    this.passwordResetExpiresAt = expiresAt;
  }
  public void resetPassword(String passwordHash) {
    this.passwordHash = passwordHash;
    this.passwordResetTokenHash = null;
    this.passwordResetExpiresAt = null;
  }
}
