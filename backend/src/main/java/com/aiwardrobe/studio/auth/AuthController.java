package com.aiwardrobe.studio.auth;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.transaction.Transactional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Value;
import com.aiwardrobe.studio.storage.WardrobeStorageService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AppUserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicy passwordPolicy;
  private final EmailVerificationService emailVerification;
  private final PasswordResetService passwordReset;
  private final WardrobeStorageService storage;
  private final boolean googleEnabled;
  private final HttpSessionSecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

  public AuthController(AppUserRepository users, PasswordEncoder passwordEncoder,
      PasswordPolicy passwordPolicy, EmailVerificationService emailVerification, PasswordResetService passwordReset, WardrobeStorageService storage,
      @Value("${app.google.client-id:}") String googleClientId,
      @Value("${app.google.client-secret:}") String googleClientSecret) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.passwordPolicy = passwordPolicy;
    this.emailVerification = emailVerification;
    this.passwordReset = passwordReset;
    this.storage = storage;
    this.googleEnabled = !googleClientId.isBlank() && !googleClientSecret.isBlank();
  }

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) { return Map.of("token", token.getToken()); }

  @GetMapping("/google/status")
  public Map<String, Boolean> googleStatus() { return Map.of("enabled", googleEnabled); }

  @GetMapping("/google/pending")
  public Map<String, Object> pendingGoogleProfile(HttpServletRequest request) {
    Object email = request.getSession(false) == null ? null : request.getSession(false).getAttribute(GoogleOnboardingSession.EMAIL);
    return email instanceof String value
        ? Map.of("pending", true, "email", value)
        : Map.of("pending", false);
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public AuthResponse register(@Valid @RequestBody AuthRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
    String username = normalize(request.username());
    String email = request.email() == null ? "" : request.email().trim().toLowerCase(java.util.Locale.ROOT);
    if (email.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required.");
    if (!request.password().equals(request.confirmPassword())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords do not match.");
    passwordPolicy.validate(request.password(), username, email);
    if (request.humanLeft() == null || request.humanRight() == null || request.humanAnswer() == null
        || request.humanLeft() < 1 || request.humanLeft() > 9 || request.humanRight() < 1 || request.humanRight() > 9
        || request.humanAnswer() != request.humanLeft() + request.humanRight()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please answer the human check correctly.");
    }
    if (users.existsByEmailIgnoreCase(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists.");
    if (users.existsByUsernameIgnoreCase(username)) throw new ResponseStatusException(HttpStatus.CONFLICT, "This username already exists.");
    AppUser user;
    try {
      user = users.save(new AppUser(UUID.randomUUID(), username, email, passwordEncoder.encode(request.password()), Instant.now()));
      String verificationToken = emailVerification.issueToken(user);
      users.save(user);
      emailVerification.send(user, verificationToken);
    } catch (DataIntegrityViolationException error) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this username or email already exists.");
    }
    return new AuthResponse(false, user.getUsername());
  }

  @PostMapping("/login")
  public AuthResponse login(@Valid @RequestBody AuthRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
    AppUser user = users.findByUsernameIgnoreCase(normalize(request.username()))
        .orElseThrow(() -> unauthorized());
    if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) throw unauthorized();
    if (!user.isEmailVerified()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verify your email before logging in. Check your inbox for the verification link.");
    }
    httpRequest.getSession().invalidate();
    authenticate(user, httpRequest, response);
    return new AuthResponse(true, user.getUsername());
  }

  @GetMapping("/me")
  public AuthResponse me(Authentication authentication) { return new AuthResponse(true, authentication.getName()); }

  @GetMapping("/profile")
  public AccountProfile profile(Authentication authentication) {
    AppUser user = users.findByUsernameIgnoreCase(authentication.getName())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account was not found."));
    String method = user.getOauthProvider() == null ? "Email and password" : "Google and password";
    return new AccountProfile(user.getUsername(), user.getEmail(), user.getPasswordHash() != null, method);
  }

  @PostMapping("/google/complete")
  @Transactional
  public AuthResponse completeGoogleProfile(@Valid @RequestBody GoogleProfileRequest body,
      HttpServletRequest request, HttpServletResponse response) {
    Object pendingEmail = request.getSession(false) == null ? null : request.getSession(false).getAttribute(GoogleOnboardingSession.EMAIL);
    Object pendingSubject = request.getSession(false) == null ? null : request.getSession(false).getAttribute(GoogleOnboardingSession.SUBJECT);
    if (!(pendingEmail instanceof String email) || !(pendingSubject instanceof String subject)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Your Google setup session expired. Continue with Google again.");
    }
    String username = normalize(body.username());
    if (!body.password().equals(body.confirmPassword())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords do not match.");
    }
    passwordPolicy.validate(body.password(), username, email);
    AppUser existingGoogleUser = users.findByOauthProviderAndOauthSubject("google", subject).orElse(null);
    if (users.existsByUsernameIgnoreCase(username)
        && (existingGoogleUser == null || !existingGoogleUser.getUsername().equalsIgnoreCase(username))) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "This username already exists.");
    }
    try {
      AppUser user;
      if (existingGoogleUser != null) {
        existingGoogleUser.completeGoogleProfile(username, passwordEncoder.encode(body.password()));
        user = users.save(existingGoogleUser);
      } else {
        user = users.save(AppUser.googleUser(UUID.randomUUID(), username, email, subject,
            passwordEncoder.encode(body.password()), Instant.now()));
      }
      request.getSession().removeAttribute(GoogleOnboardingSession.EMAIL);
      request.getSession().removeAttribute(GoogleOnboardingSession.SUBJECT);
      authenticate(user, request, response);
      return new AuthResponse(true, user.getUsername());
    } catch (DataIntegrityViolationException error) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "This username is already in use.");
    }
  }

  @DeleteMapping("/account")
  @Transactional
  public Map<String, Boolean> deleteAccount(@RequestBody Map<String, String> body, Authentication authentication,
      HttpServletRequest request) {
    AppUser user = users.findByUsernameIgnoreCase(authentication.getName())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account was not found."));
    String confirmation = body.getOrDefault("confirmation", "").trim();
    if (!user.getUsername().equalsIgnoreCase(confirmation)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type your username exactly to confirm account deletion.");
    }

    // R2 is deleted first. If storage cleanup fails, the database account remains so cleanup can be retried.
    storage.deleteAllForUser(user.getId());
    users.delete(user); // wardrobe_items are removed by the database ON DELETE CASCADE constraint.
    users.flush();
    if (request.getSession(false) != null) {
      request.getSession(false).invalidate();
    }
    SecurityContextHolder.clearContext();
    return Map.of("deleted", true);
  }

  @PostMapping("/verify")
  @Transactional
  public Map<String, String> verify(@RequestBody Map<String, String> body) {
    String token = body.getOrDefault("token", "").trim();
    if (token.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verification token is required.");
    AppUser user = users.findByVerificationTokenHash(emailVerification.hash(token))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "This verification link is invalid or has already been used."));
    if (user.getVerificationExpiresAt() == null || user.getVerificationExpiresAt().isBefore(Instant.now())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This verification link has expired. Request a new link.");
    }
    user.markEmailVerified();
    users.save(user);
    return Map.of("message", "Email verified. You can now log in.");
  }

  @PostMapping("/resend-verification")
  @Transactional
  public Map<String, String> resendVerification(@RequestBody Map<String, String> body) {
    String email = body.getOrDefault("email", "").trim().toLowerCase(java.util.Locale.ROOT);
    if (!email.isBlank()) {
      users.findByEmailIgnoreCase(email).filter(user -> !user.isEmailVerified()).ifPresent(user -> {
        String token = emailVerification.issueToken(user);
        users.save(user);
        emailVerification.send(user, token);
      });
    }
    return Map.of("message", "If an unverified account exists for that email, a new verification link has been sent.");
  }

  @PostMapping("/forgot-password")
  @Transactional
  public Map<String, String> forgotPassword(@RequestBody Map<String, String> body) {
    String email = body.getOrDefault("email", "").trim().toLowerCase(java.util.Locale.ROOT);
    if (!email.isBlank()) {
      users.findByEmailIgnoreCase(email).filter(user -> user.getPasswordHash() != null).ifPresent(user -> {
        String token = passwordReset.issueToken(user);
        users.save(user);
        try {
          passwordReset.send(user, token);
        } catch (ResponseStatusException ignored) {
          // Always return the same response so this endpoint cannot reveal registered emails.
        }
      });
    }
    return Map.of("message", "If an account exists for that email, a password-reset link has been sent.");
  }

  @PostMapping("/reset-password")
  @Transactional
  public Map<String, String> resetPassword(@RequestBody Map<String, String> body) {
    String token = body.getOrDefault("token", "").trim();
    String password = body.getOrDefault("password", "");
    String confirmPassword = body.getOrDefault("confirmPassword", "");
    if (token.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset token is required.");
    if (!password.equals(confirmPassword)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords do not match.");
    AppUser user = users.findByPasswordResetTokenHash(emailVerification.hash(token))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "This reset link is invalid or has already been used."));
    if (user.getPasswordResetExpiresAt() == null || user.getPasswordResetExpiresAt().isBefore(Instant.now())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This reset link has expired. Request a new one.");
    }
    passwordPolicy.validate(password, user.getUsername(), user.getEmail());
    user.resetPassword(passwordEncoder.encode(password));
    users.save(user);
    return Map.of("message", "Password changed successfully. You can now log in.");
  }

  private void authenticate(AppUser user, HttpServletRequest request, HttpServletResponse response) {
    Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(user.getUsername(), null, java.util.List.of());
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    contextRepository.saveContext(context, request, response);
  }
  private String normalize(String username) { return username.trim().toLowerCase(java.util.Locale.ROOT); }
  private ResponseStatusException unauthorized() { return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password."); }
}
