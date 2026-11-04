package com.finora.app.planning;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface PlanningProfileRepository extends JpaRepository<PlanningProfile,Long> { @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select p from PlanningProfile p where p.userId=:userId")
 Optional<PlanningProfile> lockedByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);
 Optional<PlanningProfile> findByUserId(Long userId); }
