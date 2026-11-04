package com.finora.app.auth.google;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
public interface GoogleAuthFlowRepository extends JpaRepository<GoogleAuthFlow,String> {
 @Query("select coalesce(sum(f.linkAttempts),0) from GoogleAuthFlow f where f.linkUserId=:userId and f.expiresAt>:since")
 long recentLinkAttempts(@Param("userId")Long userId,@Param("since")java.time.Instant since);
 @org.springframework.data.jpa.repository.Modifying
 @Query("delete from GoogleAuthFlow f where f.expiresAt<:before")
 void removeExpired(@Param("before")java.time.Instant before);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select f from GoogleAuthFlow f where f.stateHash=:hash")
 Optional<GoogleAuthFlow> locked(@Param("hash")String hash);
}
