package com.finora.app.privacy;

import com.finora.app.auth.google.AccountSessions;
import com.finora.app.auth.google.AuthCookies;
import com.finora.app.auth.google.SessionOriginFilter;
import com.finora.app.shared.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class PrivacyController {
    private final AccountDeletionService deletion;
    private final AccountSessions sessions;
    private final AuthCookies cookies;
    private final SessionOriginFilter origins;

    public PrivacyController(AccountDeletionService deletion, AccountSessions sessions,
                             AuthCookies cookies, SessionOriginFilter origins) {
        this.deletion = deletion;
        this.sessions = sessions;
        this.cookies = cookies;
        this.origins = origins;
    }

    public record Confirmation(boolean confirmed, String confirmationText) {}

    @PostMapping({"/api/account/deletion-request", "/api/v1/account/deletion-request"})
    public Map<String, String> request(Authentication auth, @RequestBody Confirmation body,
                                      HttpServletRequest request, HttpServletResponse response) {
        if (body == null || !body.confirmed() || !"ELIMINAR".equals(body.confirmationText())) {
            throw new ApiException(400, "CONFIRMATION_REQUIRED",
                "Para eliminar tu cuenta definitivamente, escribe ELIMINAR.");
        }
        if (auth == null || !auth.isAuthenticated()
            || !origins.allowed(request)
            || !sessions.userId(cookies.read(request, AuthCookies.SESSION))
                .filter(id -> id.equals(auth.getPrincipal())).isPresent()) {
            throw new ApiException(403, "SESSION_PROOF_REQUIRED",
                "Confirma la eliminación desde una sesión de Sarela en este navegador.");
        }

        deletion.delete((Long)auth.getPrincipal());

        // The old session must not survive an account deletion or be re-used.
        cookies.clear(response, AuthCookies.SESSION);
        cookies.clear(response, AuthCookies.GMAIL_FLOW);
        cookies.clear(response, AuthCookies.GOOGLE_FLOW);
        response.setHeader("Cache-Control", "no-store");
        return Map.of(
            "status", "DELETED",
            "message", "Tu cuenta y sus datos activos se eliminaron. Si vuelves a entrar con Google, empezarás con una cuenta nueva."
        );
    }
}
