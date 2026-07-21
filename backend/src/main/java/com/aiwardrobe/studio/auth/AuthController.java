package com.aiwardrobe.studio.auth;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AppUserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final HttpSessionSecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

  public AuthController(AppUserRepository users, PasswordEncoder passwordEncoder) {
    this.users = users; this.passwordEncoder = passwordEncoder;
  }

  @GetMapping("/csrf")
  public Map<String, String> csrf(CsrfToken token) { return Map.of("token", token.getToken()); }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthResponse register(@Valid @RequestBody AuthRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
    String username = normalize(request.username());
    String email = request.email() == null ? "" : request.email().trim().toLowerCase(java.util.Locale.ROOT);
    if (email.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required.");
    if (!request.password().equals(request.confirmPassword())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords do not match.");
    if (request.humanLeft() == null || request.humanRight() == null || request.humanAnswer() == null
        || request.humanLeft() < 1 || request.humanLeft() > 9 || request.humanRight() < 1 || request.humanRight() > 9
        || request.humanAnswer() != request.humanLeft() + request.humanRight()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please answer the human check correctly.");
    }
    if (users.existsByUsernameIgnoreCase(username)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken.");
    if (users.existsByEmailIgnoreCase(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered.");
    AppUser user;
    try {
      user = users.save(new AppUser(UUID.randomUUID(), username, email, passwordEncoder.encode(request.password()), Instant.now()));
    } catch (DataIntegrityViolationException error) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email is already registered.");
    }
    authenticate(user, httpRequest, response);
    return new AuthResponse(true, user.getUsername());
  }

  @PostMapping("/login")
  public AuthResponse login(@Valid @RequestBody AuthRequest request, HttpServletRequest httpRequest, HttpServletResponse response) {
    AppUser user = users.findByUsernameIgnoreCase(normalize(request.username()))
        .orElseThrow(() -> unauthorized());
    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) throw unauthorized();
    httpRequest.getSession().invalidate();
    authenticate(user, httpRequest, response);
    return new AuthResponse(true, user.getUsername());
  }

  @GetMapping("/me")
  public AuthResponse me(Authentication authentication) { return new AuthResponse(true, authentication.getName()); }

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
