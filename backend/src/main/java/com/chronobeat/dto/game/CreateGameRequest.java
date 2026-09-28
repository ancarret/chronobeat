package com.chronobeat.dto.game;

import com.chronobeat.domain.GameMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateGameRequest(
        @NotNull GameMode mode,
        @NotEmpty @Size(max = 8) List<@NotEmpty @Size(max = 60) String> playerNames,
        @NotNull @Valid GameSettingsRequest settings) {}
