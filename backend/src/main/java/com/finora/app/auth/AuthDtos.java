package com.finora.app.auth;
import jakarta.validation.constraints.*;
public final class AuthDtos {
  private AuthDtos(){}
  public record RegisterRequest(@NotBlank String name,@Email @NotBlank String email,@Size(min=8) String password){@Override public String toString(){return "Credentials[REDACTED]";}}
  public record LoginRequest(@Email @NotBlank String email,@NotBlank String password){@Override public String toString(){return "Credentials[REDACTED]";}}
  public record AuthResponse(String token,Long userId,String name,String email,String role){@Override public String toString(){return "AuthResponse[REDACTED]";}}
}
