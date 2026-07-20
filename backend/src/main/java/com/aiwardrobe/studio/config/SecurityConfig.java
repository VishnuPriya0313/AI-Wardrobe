package com.aiwardrobe.studio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class SecurityConfig {
  @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrfRepository.setCookiePath("/");
    CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
    csrfHandler.setCsrfRequestAttributeName(null);

    http
        .cors(cors -> {})
        .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository).csrfTokenRequestHandler(csrfHandler))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.GET, "/api/health", "/api/auth/csrf").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
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
    return http.build();
  }
}
