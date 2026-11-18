package com.finora.app.auth.google;

import com.finora.app.auth.User;
import com.finora.app.auth.UserRepository;
import com.finora.app.shared.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Explicitly opt-in development login. Never enabled under the production profile.
 * Uses the same database-backed session as Google, but no Google credentials.
 */
@RestController
@Profile("local")
@ConditionalOnProperty(prefix = "app.dev-login", name = "enabled", havingValue = "true")
@RequestMapping({"/api/auth/dev", "/api/v1/auth/dev"})
public class LocalDemoLoginController {
  private static final String DEMO_EMAIL = "demo@sarela.invalid";
  private final UserRepository users;
  private final BCryptPasswordEncoder passwords;
  private final AccountSessions sessions;
  private final AuthCookies cookies;
  private final SessionOriginFilter origins;

  public LocalDemoLoginController(UserRepository users, BCryptPasswordEncoder passwords,
      AccountSessions sessions, AuthCookies cookies, SessionOriginFilter origins) {
    this.users = users;
    this.passwords = passwords;
    this.sessions = sessions;
    this.cookies = cookies;
    this.origins = origins;
  }

  @PostMapping("/login")
  @Transactional
  public Map<String, String> login(HttpServletRequest request, HttpServletResponse response) {
    if (!isLocal(request) || !origins.allowed(request)) {
      throw new ApiException(403, "DEMO_LOGIN_FORBIDDEN", "El modo demo solo está disponible en localhost.");
    }

    User user = users.findByEmailIgnoreCase(DEMO_EMAIL).orElseGet(() -> {
      User created = new User("Usuario Demo", DEMO_EMAIL, passwords.encode(AuthSecrets.random()));
      created.role = "USER";
      return users.saveAndFlush(created);
    });
    if (!"USER".equals(user.role) || user.googleSubject != null) {
      throw new ApiException(409, "DEMO_ACCOUNT_CONFLICT", "La cuenta demo local no es válida.");
    }

    String token = sessions.issue(user.id);
    cookies.set(response, AuthCookies.SESSION, token, Duration.ofHours(8));
    response.setHeader("Cache-Control", "no-store");
    return Map.of("status", "DEMO_SESSION_CREATED");
  }

  private boolean isLocal(HttpServletRequest request) {
    try {
      return InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress();
    } catch (Exception e) {
      return false;
    }
  }
}
