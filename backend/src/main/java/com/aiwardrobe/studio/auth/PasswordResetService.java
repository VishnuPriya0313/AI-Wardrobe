package com.aiwardrobe.studio.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class PasswordResetService {
  private final JavaMailSender mailSender;
  private final EmailVerificationService tokenHasher;
  private final String mailFrom;
  private final String frontendBaseUrl;
  private final Duration tokenTtl;
  private final SecureRandom secureRandom = new SecureRandom();

  public PasswordResetService(JavaMailSender mailSender, EmailVerificationService tokenHasher,
      @Value("${app.mail.from:}") String mailFrom,
      @Value("${app.frontend-base-url}") String frontendBaseUrl,
      @Value("${app.password-reset.ttl:30m}") Duration tokenTtl) {
    this.mailSender = mailSender;
    this.tokenHasher = tokenHasher;
    this.mailFrom = mailFrom;
    this.frontendBaseUrl = frontendBaseUrl;
    this.tokenTtl = tokenTtl;
  }

  public String issueToken(AppUser user) {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    user.setPasswordReset(tokenHasher.hash(token), Instant.now().plus(tokenTtl));
    return token;
  }

  public void send(AppUser user, String token) {
    if (mailFrom.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
        "Password reset email is not configured.");
    String link = UriComponentsBuilder.fromUriString(frontendBaseUrl)
        .queryParam("resetToken", token).build().encode().toUriString();
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(mailFrom);
    message.setTo(user.getEmail());
    message.setSubject("Reset your AI Wardrobe password");
    message.setText("Open this link to choose a new AI Wardrobe password:\n\n" + link
        + "\n\nThis one-time link expires in " + tokenTtl.toMinutes()
        + " minutes. If you did not request this, ignore this email.");
    try { mailSender.send(message); }
    catch (MailException error) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "We could not send the reset email. Check the SMTP settings and try again.", error);
    }
  }
}
