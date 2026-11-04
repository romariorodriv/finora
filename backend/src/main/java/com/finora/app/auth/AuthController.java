package com.finora.app.auth;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/auth", "/api/v1/auth"})
public class AuthController {
  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping("/register")
  public ResponseEntity<AuthDtos.AuthResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest r) {
    throw new com.finora.app.shared.error.ApiException(410,"GOOGLE_SIGN_IN_REQUIRED","Continúa con Google para crear tu cuenta.");
  }

  @PostMapping("/login")
  public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest r) {
    throw new com.finora.app.shared.error.ApiException(410,"GOOGLE_SIGN_IN_REQUIRED","Continúa con Google para ingresar.");
  }
}
