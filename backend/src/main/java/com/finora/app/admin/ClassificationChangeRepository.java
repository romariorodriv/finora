package com.finora.app.admin;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ClassificationChangeRepository extends JpaRepository<ClassificationChange,Long> {
 List<ClassificationChange> findByBatchIdOrderByTransactionIdAsc(String id);
}
