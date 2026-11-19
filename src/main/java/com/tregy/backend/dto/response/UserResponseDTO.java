package com.tregy.backend.dto.response;
import com.tregy.backend.model.enums.PlatformRole; import java.time.Instant;
public record UserResponseDTO(String id,String email,String uiLanguage,PlatformRole platformRole,Instant emailVerifiedAt){}
