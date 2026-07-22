package com.aiwardrobe.studio.auth;

import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PasswordPolicy {
  public void validate(String password, String username, String email) {
    if (password == null || password.length() < 8) fail("Password must be at least 8 characters.");
    if (!password.matches(".*[A-Za-z].*")) fail("Password must include at least one letter.");
    if (!password.matches(".*[0-9].*")) fail("Password must include at least one number.");
    if (!password.matches(".*[^A-Za-z0-9\\s].*")) fail("Password must include at least one special character.");

    String normalizedPassword = password.toLowerCase(Locale.ROOT);
    String normalizedUsername = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    if (normalizedUsername.length() >= 3 && normalizedPassword.contains(normalizedUsername)) {
      fail("Password must not contain your username.");
    }

    String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    String emailLocalPart = normalizedEmail.contains("@") ? normalizedEmail.substring(0, normalizedEmail.indexOf('@')) : normalizedEmail;
    if (emailLocalPart.length() >= 3 && normalizedPassword.contains(emailLocalPart)) {
      fail("Password must not contain your email name.");
    }
  }

  private void fail(String message) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
