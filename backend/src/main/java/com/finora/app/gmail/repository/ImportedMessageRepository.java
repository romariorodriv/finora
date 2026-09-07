package com.finora.app.gmail.repository;
import com.finora.app.gmail.entity.ImportedMessage;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ImportedMessageRepository extends JpaRepository<ImportedMessage, Long> {
    boolean existsByUserIdAndExternalMessageId(Long userId, String externalMessageId);
}
