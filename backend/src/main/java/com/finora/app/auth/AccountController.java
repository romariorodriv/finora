package com.finora.app.auth;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import com.finora.app.shared.error.ApiException;
@RestController
public class AccountController {
 private final UserRepository users;public AccountController(UserRepository users){this.users=users;}
 public record Profile(Long userId,String name,String email,String role){}
 @GetMapping({"/api/account","/api/v1/account"}) public Profile account(Authentication a){var u=users.findById((Long)a.getPrincipal()).orElseThrow(()->new ApiException(401,"ACCOUNT_NOT_FOUND","Sesión no válida."));return new Profile(u.id,u.name,u.email,u.role);}
}
