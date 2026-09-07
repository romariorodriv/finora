package com.finora.app.auth;
import jakarta.validation.constraints.*;
public final class AuthDtos {
  private AuthDtos(){}
  public record RegisterRequest(@NotBlank String name,@Email @NotBlank String email,@Size(min=8) String password){}
  public record LoginRequest(@Email @NotBlank String email,@NotBlank String password){}
  public record AuthResponse(String token,Long userId,String name,String email){}
}
