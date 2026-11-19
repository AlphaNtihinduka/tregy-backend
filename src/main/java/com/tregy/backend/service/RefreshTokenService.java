package com.tregy.backend.service;
import com.tregy.backend.service.support.*;
public interface RefreshTokenService{IssuedToken issue(String userId,String deviceId); RefreshRotation rotate(String raw); void revoke(String raw); void revokeAll(String userId);}
