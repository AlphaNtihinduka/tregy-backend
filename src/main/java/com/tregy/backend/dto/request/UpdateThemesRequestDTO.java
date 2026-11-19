package com.tregy.backend.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateThemesRequestDTO(@NotEmpty @Size(max = 12) List<String> themes) {
}
