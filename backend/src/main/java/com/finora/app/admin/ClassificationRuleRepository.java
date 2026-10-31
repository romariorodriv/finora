package com.finora.app.admin;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface ClassificationRuleRepository extends JpaRepository<ClassificationRule,Long>,JpaSpecificationExecutor<ClassificationRule> {
 @Query("select r from ClassificationRule r where r.active=true and (r.userId is null or r.userId=:uid)")
 List<ClassificationRule> eligible(@Param("uid") Long uid);
 boolean existsByMatchTypeAndMerchantKeyAndScopeKeyAndIdNot(String type,String key,String scope,Long id);
}
