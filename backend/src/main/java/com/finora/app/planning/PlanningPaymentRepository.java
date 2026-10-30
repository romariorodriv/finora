package com.finora.app.planning;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PlanningPaymentRepository extends JpaRepository<PlanningPayment,Long> {
 List<PlanningPayment> findByUserIdOrderByDueDateAscIdAsc(Long userId);
 long countByUserId(Long userId);
}
