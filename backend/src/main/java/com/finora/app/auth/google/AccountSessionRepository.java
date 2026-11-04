package com.finora.app.auth.google;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AccountSessionRepository extends JpaRepository<AccountSession,String> {
 @org.springframework.data.jpa.repository.Modifying
 @org.springframework.data.jpa.repository.Query("delete from AccountSession s where s.expiresAt<:before")
 void removeExpired(@org.springframework.data.repository.query.Param("before")java.time.Instant before);
}
