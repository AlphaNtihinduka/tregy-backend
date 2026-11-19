package com.tregy.backend.repository;
import com.tregy.backend.model.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
public interface RefreshTokenRepository extends JpaRepository<RefreshToken,String>{
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from RefreshToken t where t.tokenHash=:hash") Optional<RefreshToken> findForUpdate(@Param("hash") String hash);
 @Modifying @Query("update RefreshToken t set t.revokedAt=:now where t.userId=:userId and t.revokedAt is null") int revokeUser(@Param("userId") String userId,@Param("now") Instant now);
 @Modifying @Query("update RefreshToken t set t.revokedAt=:now where t.familyId=:family and t.revokedAt is null") int revokeFamily(@Param("family") String family,@Param("now") Instant now);
}
