package com.tregy.backend.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
/** The caller's own password, required to delete the account so a stolen access token alone cannot. */
public record DeleteAccountRequestDTO(@NotBlank @Size(max = 200) String password){}
