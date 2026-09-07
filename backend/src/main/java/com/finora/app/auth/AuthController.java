package com.finora.app.auth;
import com.finora.app.config.JwtService; import jakarta.validation.Valid;
import org.springframework.http.*; import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth")
public class AuthController {
  private final UserRepository users; private final BCryptPasswordEncoder encoder; private final JwtService jwt;
  public AuthController(UserRepository u,BCryptPasswordEncoder e,JwtService j){users=u;encoder=e;jwt=j;}
  @PostMapping("/register") public ResponseEntity<?> register(@Valid @RequestBody AuthDtos.RegisterRequest r){
    if(users.findByEmailIgnoreCase(r.email()).isPresent())return ResponseEntity.status(409).body(java.util.Map.of("message","El correo ya está registrado"));
    User u=users.save(new User(r.name().trim(),r.email().toLowerCase(),encoder.encode(r.password())));return ResponseEntity.status(201).body(out(u));}
  @PostMapping("/login") public ResponseEntity<?> login(@Valid @RequestBody AuthDtos.LoginRequest r){
    return users.findByEmailIgnoreCase(r.email()).filter(u->encoder.matches(r.password(),u.passwordHash)).<ResponseEntity<?>>map(u->ResponseEntity.ok(out(u))).orElseGet(()->ResponseEntity.status(401).body(java.util.Map.of("message","Correo o contraseña incorrectos")));}
  private AuthDtos.AuthResponse out(User u){return new AuthDtos.AuthResponse(jwt.issue(u.id,u.email),u.id,u.name,u.email);}
}
