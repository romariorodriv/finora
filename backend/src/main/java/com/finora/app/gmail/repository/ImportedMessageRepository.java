package com.finora.app.gmail.repository;
import com.finora.app.gmail.entity.ImportedMessage;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ImportedMessageRepository extends JpaRepository<ImportedMessage, Long> {
    java.util.Optional<ImportedMessage> findByUserIdAndGmailConnectionIdAndExternalMessageId(Long userId,Long connectionId,String messageId);
    org.springframework.data.domain.Page<ImportedMessage> findByUserIdAndProcessingStatusIn(Long userId,java.util.Collection<com.finora.app.gmail.entity.ImportedMessage.Status> statuses,org.springframework.data.domain.Pageable page);
    boolean existsByUserIdAndExternalMessageId(Long userId, String externalMessageId);
}
