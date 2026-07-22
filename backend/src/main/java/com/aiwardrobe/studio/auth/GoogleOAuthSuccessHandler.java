package com.aiwardrobe.studio.auth;

import java.io.IOException;
import java.util.Locale;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
public class GoogleOAuthSuccessHandler implements AuthenticationSuccessHandler {
  private final AppUserRepository users;
  private final String frontendBaseUrl;
  private final HttpSessionSecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

  public GoogleOAuthSuccessHandler(AppUserRepository users, @Value("${app.frontend-base-url}") String frontendBaseUrl) {
    this.users = users;
    this.frontendBaseUrl = frontendBaseUrl;
  }

  @Override
  public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
      Authentication authentication) throws IOException, ServletException {
    if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)
        || oidcUser.getEmail() == null || !Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
      response.sendRedirect(frontendBaseUrl + "?googleError=email_not_verified");
      return;
    }

    String email = oidcUser.getEmail().trim().toLowerCase(Locale.ROOT);
    String subject = oidcUser.getSubject();
    AppUser user = users.findByOauthProviderAndOauthSubject("google", subject)
        .orElseGet(() -> users.findByEmailIgnoreCase(email).map(existing -> {
          existing.linkGoogle(subject);
          return users.save(existing);
        }).orElse(null));

    if (user == null || user.getPasswordHash() == null) {
      if (request.getSession(false) != null) {
        request.getSession(false).invalidate();
      }
      request.getSession(true).setAttribute(GoogleOnboardingSession.EMAIL, email);
      request.getSession().setAttribute(GoogleOnboardingSession.SUBJECT, subject);
      SecurityContext emptyContext = SecurityContextHolder.createEmptyContext();
      SecurityContextHolder.setContext(emptyContext);
      contextRepository.saveContext(emptyContext, request, response);
      response.sendRedirect(frontendBaseUrl + "?googleOnboarding=required");
      return;
    }

    Authentication appAuthentication = UsernamePasswordAuthenticationToken.authenticated(
        user.getUsername(), null, java.util.List.of());
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(appAuthentication);
    SecurityContextHolder.setContext(context);
    contextRepository.saveContext(context, request, response);
    response.sendRedirect(frontendBaseUrl + "?googleLogin=success");
  }

}
