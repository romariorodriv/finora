package com.finora.app.transaction;
import org.springframework.data.jpa.repository.JpaRepository; import java.time.LocalDate; import java.util.*;
public interface TransactionRepository extends JpaRepository<Transaction,Long>,org.springframework.data.jpa.repository.JpaSpecificationExecutor<Transaction>{
 long countByCategoryIdAndType(Long categoryId,String type);
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select t from Transaction t where t.id=:id")
 Optional<Transaction> locked(@org.springframework.data.repository.query.Param("id") Long id);
 List<Transaction> findByUserIdAndDateBetweenOrderByDateDesc(Long uid,LocalDate from,LocalDate to);
 Optional<Transaction> findByIdAndUserId(Long id,Long uid); boolean existsByUserIdAndExternalId(Long uid,String externalId);
}
