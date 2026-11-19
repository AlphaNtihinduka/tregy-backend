package com.tregy.backend.service;
import com.tregy.backend.dto.request.UpdateMeRequestDTO; import com.tregy.backend.dto.response.UserResponseDTO;
public interface MeService{UserResponseDTO get(String userId);UserResponseDTO update(String userId,UpdateMeRequestDTO request);}
