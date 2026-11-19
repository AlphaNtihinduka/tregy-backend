package com.tregy.backend.dto.request;
import jakarta.validation.constraints.*;
public record ResetPasswordRequestDTO(@NotBlank String token,@NotBlank @Size(min=12,max=72) String newPassword){}
