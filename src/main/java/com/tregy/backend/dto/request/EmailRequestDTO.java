package com.tregy.backend.dto.request;
import jakarta.validation.constraints.*;
public record EmailRequestDTO(@NotBlank @Email @Size(max=254) String email){
 // Phone keyboards often add a trailing space; trim before validation so it is not a 400.
 public EmailRequestDTO{if(email!=null)email=email.strip();}
}
