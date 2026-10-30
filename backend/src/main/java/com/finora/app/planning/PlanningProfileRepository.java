package com.finora.app.planning;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface PlanningProfileRepository extends JpaRepository<PlanningProfile,Long> { Optional<PlanningProfile> findByUserId(Long userId); }
