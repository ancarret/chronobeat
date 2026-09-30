package com.chronobeat.dto.room;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinRoomRequest(@NotBlank @Size(max = 60) String nickname) {}
