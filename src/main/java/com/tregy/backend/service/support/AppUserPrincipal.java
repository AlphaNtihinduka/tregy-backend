package com.tregy.backend.service.support;
import com.tregy.backend.model.AppUser;import org.springframework.security.core.authority.SimpleGrantedAuthority;import java.util.List;
public record AppUserPrincipal(String userId,String email,String role){public static AppUserPrincipal from(AppUser u){return new AppUserPrincipal(u.getId(),u.getEmail(),u.getPlatformRole().name());}public List<SimpleGrantedAuthority> authorities(){return List.of(new SimpleGrantedAuthority("ROLE_"+role));}}
