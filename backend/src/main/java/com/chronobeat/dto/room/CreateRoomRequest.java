package com.chronobeat.dto.room;

import com.chronobeat.dto.game.GameSettingsRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(@NotBlank @Size(max = 60) String nickname, @NotNull @Valid GameSettingsRequest settings) {}
