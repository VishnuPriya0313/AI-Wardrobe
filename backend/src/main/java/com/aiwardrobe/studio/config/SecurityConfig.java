package com.aiwardrobe.studio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import com.aiwardrobe.studio.auth.GoogleOAuthSuccessHandler;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@Configuration
public class SecurityConfig {
  @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http,
      ObjectProvider<ClientRegistrationRepository> registrations,
      GoogleOAuthSuccessHandler googleSuccessHandler,
      @Value("${app.frontend-base-url}") String frontendBaseUrl) throws Exception {
    CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrfRepository.setCookiePath("/");
    CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
    csrfHandler.setCsrfRequestAttributeName(null);

    http
        .cors(cors -> {})
        .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository).csrfTokenRequestHandler(csrfHandler))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.GET, "/api/health", "/api/auth/csrf", "/api/auth/google/status", "/api/auth/google/pending").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/verify", "/api/auth/resend-verification", "/api/auth/forgot-password", "/api/auth/reset-password", "/api/auth/google/complete").permitAll()
            .requestMatchers("/error", "/oauth2/**", "/login/oauth2/**").permitAll()
            .anyRequest().authenticated())
        .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, error) -> {
          response.setStatus(401);
          response.setContentType("application/json");
          response.getWriter().write("{\"error\":\"Authentication required.\"}");
        }))
        .logout(logout -> logout
            .logoutUrl("/api/auth/logout")
            .deleteCookies("JSESSIONID")
            .invalidateHttpSession(true)
            .clearAuthentication(true)
            .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));
    if (registrations.getIfAvailable() != null) {
      http.oauth2Login(oauth -> oauth
          .successHandler(googleSuccessHandler)
          .failureHandler((request, response, error) -> response.sendRedirect(frontendBaseUrl + "?googleError=login_failed")));
    }
    return http.build();
  }
}
