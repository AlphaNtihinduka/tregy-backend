package com.tregy.backend.service;
import com.tregy.backend.dto.request.DeleteAccountRequestDTO; import com.tregy.backend.dto.request.UpdateMeRequestDTO; import com.tregy.backend.dto.response.UserResponseDTO;
public interface MeService{UserResponseDTO get(String userId);UserResponseDTO update(String userId,UpdateMeRequestDTO request);
 /** Deletes the caller's account and everything that belongs to it (ADR 0009). Needs the password. */
 void deleteAccount(String userId,DeleteAccountRequestDTO request);}
