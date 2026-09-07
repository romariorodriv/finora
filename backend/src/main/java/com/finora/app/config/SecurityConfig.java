package com.finora.app.config;
import org.springframework.context.annotation.*; import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy; import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain; import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
  @Bean BCryptPasswordEncoder encoder(){return new BCryptPasswordEncoder();}
  @Bean SecurityFilterChain chain(HttpSecurity h,JwtFilter f)throws Exception{return h.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).authorizeHttpRequests(a->a.requestMatchers("/","/index.html","/styles.css","/premium.css","/app.js","/api/auth/**","/api/health","/api/integrations/gmail/callback","/h2-console/**").permitAll().anyRequest().authenticated()).headers(x->x.frameOptions(y->y.sameOrigin())).addFilterBefore(f,UsernamePasswordAuthenticationFilter.class).build();}
}
