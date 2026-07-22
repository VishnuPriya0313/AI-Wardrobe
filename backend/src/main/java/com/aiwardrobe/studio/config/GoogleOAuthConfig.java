package com.aiwardrobe.studio.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

@Configuration
public class GoogleOAuthConfig {
  @Bean
  @ConditionalOnExpression("'${app.google.client-id:}' != '' && '${app.google.client-secret:}' != ''")
  ClientRegistrationRepository googleClientRegistrationRepository(
      @Value("${app.google.client-id}") String clientId,
      @Value("${app.google.client-secret}") String clientSecret) {
    ClientRegistration google = ClientRegistrations.fromIssuerLocation("https://accounts.google.com")
        .registrationId("google")
        .clientId(clientId)
        .clientSecret(clientSecret)
        .scope("openid", "profile", "email")
        .userNameAttributeName("sub")
        .clientName("Google")
        .build();
    return new InMemoryClientRegistrationRepository(google);
  }
}
