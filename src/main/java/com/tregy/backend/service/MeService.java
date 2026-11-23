package com.tregy.backend.service;
import com.tregy.backend.dto.request.DeleteAccountRequestDTO; import com.tregy.backend.dto.request.UpdateMeRequestDTO; import com.tregy.backend.dto.response.DeletionScheduledResponseDTO; import com.tregy.backend.dto.response.UserResponseDTO;
public interface MeService{UserResponseDTO get(String userId);UserResponseDTO update(String userId,UpdateMeRequestDTO request);
 /** Schedules deletion of the caller's account after the grace period and signs it out everywhere (ADR 0009). Needs the password. */
 DeletionScheduledResponseDTO deleteAccount(String userId,DeleteAccountRequestDTO request);}
