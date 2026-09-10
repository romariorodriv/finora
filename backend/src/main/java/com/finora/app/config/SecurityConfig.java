package com.finora.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import java.util.*;

@Configuration
public class SecurityConfig {
  @Bean
  BCryptPasswordEncoder encoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SecurityFilterChain chain(HttpSecurity h, JwtFilter f, CorsConfigurationSource corsConfigurationSource,
      @Value("${spring.h2.console.enabled:false}") boolean h2Console) throws Exception {
    return h.cors(c -> c.configurationSource(corsConfigurationSource))
        .csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a -> {
          a.requestMatchers("/api/auth/**", "/api/v1/auth/**", "/api/health", "/api/v1/health").permitAll();
          a.requestMatchers("/api/integrations/gmail/callback", "/api/v1/integrations/gmail/callback").permitAll();
          a.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll();
          if (h2Console) {
            a.requestMatchers("/h2-console/**").permitAll();
          }
          a.anyRequest().authenticated();
        })
        .headers(x -> x.frameOptions(y -> y.sameOrigin()))
        .addFilterBefore(f, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origin:http://localhost:4200}") String allowedOrigin) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(allowedOrigin));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    config.setExposedHeaders(List.of("Location"));
    config.setAllowCredentials(false);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }
}
