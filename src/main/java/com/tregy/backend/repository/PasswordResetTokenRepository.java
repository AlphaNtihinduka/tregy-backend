package com.tregy.backend.repository;
import com.tregy.backend.model.PasswordResetToken; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.util.Optional;
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken,String>{
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select t from PasswordResetToken t where t.tokenHash=:hash") Optional<PasswordResetToken> findForUpdate(@Param("hash") String hash);
 @Modifying @Query("delete from PasswordResetToken t where t.userId=:userId and t.usedAt is null") void deleteUnused(@Param("userId") String userId);
}
