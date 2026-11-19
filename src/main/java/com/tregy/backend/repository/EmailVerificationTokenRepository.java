package com.tregy.backend.repository;
import com.tregy.backend.model.EmailVerificationToken; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.util.Optional;
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken,String>{
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select t from EmailVerificationToken t where t.tokenHash=:hash") Optional<EmailVerificationToken> findForUpdate(@Param("hash") String hash);
 @Modifying @Query("delete from EmailVerificationToken t where t.userId=:userId and t.usedAt is null") void deleteUnused(@Param("userId") String userId);
}
