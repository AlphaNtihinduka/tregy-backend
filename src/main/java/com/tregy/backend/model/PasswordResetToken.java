package com.tregy.backend.model;
import jakarta.persistence.*; import lombok.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="password_reset_token") @Getter @Setter @NoArgsConstructor
public class PasswordResetToken {
 @Id @Column(length=36,updatable=false) private String id; @Column(name="user_id",nullable=false,length=36) private String userId; @ToString.Exclude @Column(name="token_hash",nullable=false,unique=true,length=64) private String tokenHash;
 @Column(name="expires_at",nullable=false) private Instant expiresAt; @Column(name="used_at") private Instant usedAt; @Column(name="created_at",nullable=false) private Instant createdAt;
 @PrePersist void create(){if(id==null)id=UUID.randomUUID().toString();if(createdAt==null)createdAt=Instant.now();} public boolean usableAt(Instant n){return usedAt==null&&expiresAt.isAfter(n);}
}
