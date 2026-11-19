package com.tregy.backend.repository;
import com.tregy.backend.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.data.jpa.repository.Lock; import org.springframework.data.jpa.repository.Query; import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface AppUserRepository extends JpaRepository<AppUser,String>{Optional<AppUser> findByEmail(String email); boolean existsByEmail(String email); @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select u from AppUser u where u.id = :id") Optional<AppUser> findByIdForUpdate(String id);}
