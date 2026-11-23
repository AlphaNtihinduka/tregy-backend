package com.tregy.backend.model;
import com.tregy.backend.model.enums.*; import jakarta.persistence.*; import lombok.*;
import java.time.Instant; import java.util.*;
@Entity @Table(name="app_user") @Getter @Setter @NoArgsConstructor
public class AppUser {
 @Id @Column(length=36,updatable=false) private String id;
 @ToString.Exclude @Column(nullable=false,unique=true,length=254) private String email;
 @ToString.Exclude @Column(name="password_hash",nullable=false,length=100) private String passwordHash;
 @Column(name="ui_language",nullable=false,length=5) private String uiLanguage="en";
 @Enumerated(EnumType.STRING) @Column(name="platform_role",nullable=false,length=20) private PlatformRole platformRole=PlatformRole.USER;
 @Column(name="email_verified_at") private Instant emailVerifiedAt; @Column(name="token_version",nullable=false) private int tokenVersion;
 @Column(name="deletion_requested_at") private Instant deletionRequestedAt;
 @Version @Column(nullable=false) private long version; @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt; @Column(name="updated_at",nullable=false) private Instant updatedAt;
 @PrePersist void create(){if(id==null)id=UUID.randomUUID().toString();email=email.strip().toLowerCase(Locale.ROOT);if(createdAt==null)createdAt=Instant.now();if(updatedAt==null)updatedAt=createdAt;}
 @PreUpdate void update(){email=email.strip().toLowerCase(Locale.ROOT);updatedAt=Instant.now();}
}
