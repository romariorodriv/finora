package com.finora.app.gmail.jobs;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
public interface GmailSyncJobRepository extends JpaRepository<GmailSyncJob,Long> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select j from GmailSyncJob j where j.userId=:userId")
 Optional<GmailSyncJob> locked(@Param("userId")Long userId);
 @Query("select j.userId from GmailSyncJob j where j.status in ('PENDING','IMPORTING') and (j.nextAttemptAt is null or j.nextAttemptAt<=:now) and (j.leaseUntil is null or j.leaseUntil<:now) order by j.updatedAt")
 List<Long> due(@Param("now")Instant now,Pageable limit);
}
