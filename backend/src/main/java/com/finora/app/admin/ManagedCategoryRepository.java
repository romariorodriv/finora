package com.finora.app.admin;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface ManagedCategoryRepository extends JpaRepository<ManagedCategory,Long>,JpaSpecificationExecutor<ManagedCategory> {
 Optional<ManagedCategory> findByNameKey(String key);
 List<ManagedCategory> findByActiveTrueOrderByNameAsc();
 List<ManagedCategory> findAllByOrderByNameAsc();
}
