package com.finora.app.gmail.jobs;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface GmailJobItemRepository extends JpaRepository<GmailJobItem,Long> {
 boolean existsByRunIdAndMessageId(String runId,String messageId);
 @Query("select i.outcome,count(i) from GmailJobItem i where i.runId=:runId and i.userId=:userId group by i.outcome")
 List<Object[]> totals(@Param("runId")String runId,@Param("userId")Long userId);
}
