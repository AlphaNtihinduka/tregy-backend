package com.tregy.backend.dto.request;
import jakarta.validation.constraints.*;
public record RegisterRequestDTO(@NotBlank @Email @Size(max=254) String email,@NotBlank @Size(min=12,max=72) String password,@NotBlank @Pattern(regexp="en|it") String uiLanguage,@NotBlank @Size(max=100) String installationId){
 // Phone keyboards often add a trailing space; trim before validation so it is not a 400.
 public RegisterRequestDTO{if(email!=null)email=email.strip();}
}
