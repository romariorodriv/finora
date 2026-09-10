package com.finora.app.auth;

import com.finora.app.config.JwtService;
import com.finora.app.shared.error.ApiException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private final UserRepository users;
  private final BCryptPasswordEncoder encoder;
  private final JwtService jwt;

  public AuthService(UserRepository users, BCryptPasswordEncoder encoder, JwtService jwt) {
    this.users = users;
    this.encoder = encoder;
    this.jwt = jwt;
  }

  @Transactional
  public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest r) {
    String email = r.email().trim().toLowerCase();
    if (users.findByEmailIgnoreCase(email).isPresent()) {
      throw new ApiException(409, "EMAIL_ALREADY_REGISTERED", "El correo ya está registrado");
    }
    User user = users.save(new User(r.name().trim(), email, encoder.encode(r.password())));
    return out(user);
  }

  public AuthDtos.AuthResponse login(AuthDtos.LoginRequest r) {
    return users.findByEmailIgnoreCase(r.email().trim())
        .filter(u -> encoder.matches(r.password(), u.passwordHash))
        .map(this::out)
        .orElseThrow(() -> new ApiException(401, "INVALID_CREDENTIALS", "Correo o contraseña incorrectos"));
  }

  private AuthDtos.AuthResponse out(User u) {
    return new AuthDtos.AuthResponse(jwt.issue(u.id, u.email), u.id, u.name, u.email);
  }
}
