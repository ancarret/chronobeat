package com.chronobeat.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProfileRequest(@NotBlank @Size(max = 40) String nickname) {}
