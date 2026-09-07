package com.finora.app.transaction;
import org.springframework.data.jpa.repository.JpaRepository; import java.time.LocalDate; import java.util.*;
public interface TransactionRepository extends JpaRepository<Transaction,Long>{
 List<Transaction> findByUserIdAndDateBetweenOrderByDateDesc(Long uid,LocalDate from,LocalDate to);
 Optional<Transaction> findByIdAndUserId(Long id,Long uid); boolean existsByUserIdAndExternalId(Long uid,String externalId);
}
