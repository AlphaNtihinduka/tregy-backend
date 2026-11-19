package com.tregy.backend.model;
import jakarta.persistence.*; import lombok.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="refresh_token") @Getter @Setter @NoArgsConstructor
public class RefreshToken {
 @Id @Column(length=36,updatable=false) private String id; @Column(name="user_id",nullable=false,updatable=false,length=36) private String userId;
 @Column(name="device_id",nullable=false,updatable=false,length=36) private String deviceId; @Column(name="family_id",nullable=false,updatable=false,length=36) private String familyId;
 @ToString.Exclude @Column(name="token_hash",nullable=false,updatable=false,unique=true,length=64) private String tokenHash;
 @Column(name="expires_at",nullable=false) private Instant expiresAt; @Column(name="revoked_at") private Instant revokedAt; @Column(name="replaced_by_id",length=36) private String replacedById; @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @PrePersist void create(){if(id==null)id=UUID.randomUUID().toString();if(createdAt==null)createdAt=Instant.now();} public boolean wasRotated(){return revokedAt!=null&&replacedById!=null;}
}
