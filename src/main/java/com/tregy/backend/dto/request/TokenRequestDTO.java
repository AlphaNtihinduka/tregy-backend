package com.tregy.backend.dto.request;
import jakarta.validation.constraints.NotBlank;
public record TokenRequestDTO(@NotBlank String token){}
