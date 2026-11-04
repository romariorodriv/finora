package com.finora.app.auth.google;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
@Component @EnableScheduling
@ConditionalOnProperty(name="app.auth.maintenance-enabled",havingValue="true",matchIfMissing=true)
public class AuthMaintenance {
 private final GoogleAuthFlowRepository flows;private final AccountSessionRepository sessions;
 public AuthMaintenance(GoogleAuthFlowRepository flows,AccountSessionRepository sessions){this.flows=flows;this.sessions=sessions;}
 @Scheduled(initialDelay=3600000,fixedDelay=3600000) @Transactional
 public void removeExpired(){sessions.removeExpired(Instant.now());flows.removeExpired(Instant.now().minusSeconds(3600));}
}
