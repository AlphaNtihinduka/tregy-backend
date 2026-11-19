package com.tregy.backend.model;
import com.tregy.backend.model.enums.DevicePlatform; import jakarta.persistence.*; import lombok.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="device") @Getter @Setter @NoArgsConstructor
public class Device {
 @Id @Column(length=36,updatable=false) private String id; @Column(name="user_id",nullable=false,updatable=false,length=36) private String userId;
 @Column(name="installation_id",nullable=false,updatable=false,length=100) private String installationId; @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private DevicePlatform platform;
 @Column(name="last_seen_at",nullable=false) private Instant lastSeenAt; @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt; @Version @Column(nullable=false) private long version;
 @PrePersist void create(){if(id==null)id=UUID.randomUUID().toString();if(createdAt==null)createdAt=Instant.now();if(lastSeenAt==null)lastSeenAt=createdAt;}
}
