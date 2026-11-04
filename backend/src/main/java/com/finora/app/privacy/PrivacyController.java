package com.finora.app.privacy;
import com.finora.app.gmail.service.GmailOAuthService;
import com.finora.app.auth.UserRepository;
import com.finora.app.shared.error.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Map;
@RestController
public class PrivacyController {
 private final DeletionRequestRepository requests;private final GmailOAuthService gmail;private final UserRepository users;
 public PrivacyController(DeletionRequestRepository requests,GmailOAuthService gmail,UserRepository users){this.requests=requests;this.gmail=gmail;this.users=users;}
 public record Confirmation(boolean confirmed){}
 @PostMapping({"/api/account/deletion-request","/api/v1/account/deletion-request"}) @Transactional
 public Map<String,String> request(Authentication authentication,@RequestBody Confirmation body){
  if(!body.confirmed())throw new ApiException(400,"CONFIRMATION_REQUIRED","Confirma la solicitud de eliminación.");
  Long id=(Long)authentication.getPrincipal();users.locked(id).orElseThrow();gmail.disconnect(id);
  var row=requests.findById(id).orElseGet(DeletionRequest::new);row.userId=id;row.status="PENDING";row.requestedAt=Instant.now();row.completedAt=null;requests.save(row);
  return Map.of("status","PENDING","message","Solicitud registrada. Las importaciones se detuvieron. La eliminación será tramitada por el operador; los datos aún no se han eliminado.");
 }
}
