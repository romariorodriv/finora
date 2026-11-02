package com.finora.app.gmail.repository;
import com.finora.app.gmail.entity.GmailOAuthState;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface GmailOAuthStateRepository extends JpaRepository<GmailOAuthState, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from GmailOAuthState s where s.stateHash=:hash")
    Optional<GmailOAuthState> locked(@org.springframework.data.repository.query.Param("hash")String hash);
    Optional<GmailOAuthState> findByStateHash(String stateHash);
}
