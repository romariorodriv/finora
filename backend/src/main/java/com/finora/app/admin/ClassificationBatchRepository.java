package com.finora.app.admin;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface ClassificationBatchRepository extends JpaRepository<ClassificationBatch,String> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select b from ClassificationBatch b where b.id=:id")
 Optional<ClassificationBatch> locked(@Param("id") String id);
}
