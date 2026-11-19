package com.tregy.backend.dto.request;
import jakarta.validation.constraints.*;
public record LoginRequestDTO(@NotBlank @Email @Size(max=254) String email,@NotBlank @Size(max=72) String password,@NotBlank @Size(max=100) String installationId){
 // Phone keyboards often add a trailing space; trim before validation so it is not a 400.
 public LoginRequestDTO{if(email!=null)email=email.strip();}
}
