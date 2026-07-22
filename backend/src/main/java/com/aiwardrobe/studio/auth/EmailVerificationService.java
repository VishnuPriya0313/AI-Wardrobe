package com.aiwardrobe.studio.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class EmailVerificationService {
  private final JavaMailSender mailSender;
  private final String mailFrom;
  private final String frontendBaseUrl;
  private final Duration tokenTtl;
  private final SecureRandom secureRandom = new SecureRandom();

  public EmailVerificationService(
      JavaMailSender mailSender,
      @Value("${app.mail.from:}") String mailFrom,
      @Value("${app.frontend-base-url}") String frontendBaseUrl,
      @Value("${app.email-verification.ttl:24h}") Duration tokenTtl) {
    this.mailSender = mailSender;
    this.mailFrom = mailFrom;
    this.frontendBaseUrl = frontendBaseUrl;
    this.tokenTtl = tokenTtl;
  }

  public String issueToken(AppUser user) {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    user.setVerification(hash(token), Instant.now().plus(tokenTtl));
    return token;
  }

  public void send(AppUser user, String token) {
    if (mailFrom.isBlank()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "Email verification is not configured. Set the MAIL_FROM and SMTP environment variables.");
    }
    String link = UriComponentsBuilder.fromUriString(frontendBaseUrl)
        .queryParam("verifyToken", token)
        .build().encode().toUriString();
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(mailFrom);
    message.setTo(user.getEmail());
    message.setSubject("Verify your AI Wardrobe email");
    message.setText("Welcome to AI Wardrobe. Verify your email by opening this link:\n\n" + link
        + "\n\nThis link expires in " + tokenTtl.toHours() + " hours. If you did not create this account, ignore this email.");
    try {
      mailSender.send(message);
    } catch (MailException error) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
          "We could not send the verification email. Check the SMTP settings and try again.", error);
    }
  }

  public String hash(String token) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
