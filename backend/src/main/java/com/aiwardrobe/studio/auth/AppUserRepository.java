package com.aiwardrobe.studio.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
  Optional<AppUser> findByUsernameIgnoreCase(String username);
  Optional<AppUser> findByEmailIgnoreCase(String email);
  Optional<AppUser> findByVerificationTokenHash(String tokenHash);
  Optional<AppUser> findByPasswordResetTokenHash(String tokenHash);
  Optional<AppUser> findByOauthProviderAndOauthSubject(String provider, String subject);
  boolean existsByUsernameIgnoreCase(String username);
  boolean existsByEmailIgnoreCase(String email);
}
