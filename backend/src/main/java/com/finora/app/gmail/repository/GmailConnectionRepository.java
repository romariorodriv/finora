package com.finora.app.gmail.repository;
import com.finora.app.gmail.entity.GmailConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface GmailConnectionRepository extends JpaRepository<GmailConnection, Long> {
    Optional<GmailConnection> findFirstByUserIdAndStatusNotOrderByUpdatedAtDesc(Long userId, GmailConnection.Status status);
    Optional<GmailConnection> findByUserIdAndGmailAddress(Long userId, String gmailAddress);
}
