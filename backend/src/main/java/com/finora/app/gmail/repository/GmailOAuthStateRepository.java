package com.finora.app.gmail.repository;
import com.finora.app.gmail.entity.GmailOAuthState;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface GmailOAuthStateRepository extends JpaRepository<GmailOAuthState, Long> {
    Optional<GmailOAuthState> findByStateHash(String stateHash);
}
