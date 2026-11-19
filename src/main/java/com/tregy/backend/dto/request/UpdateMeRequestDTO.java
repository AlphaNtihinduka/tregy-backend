package com.tregy.backend.dto.request;
import jakarta.validation.constraints.*;
public record UpdateMeRequestDTO(@Pattern(regexp="en|it") String uiLanguage){}
