package com.chronobeat.dto.game;

import com.chronobeat.domain.GameMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * @param profilePlayerIndex which entry of {@code playerNames} is the caller's own profile (their
 *     records are tracked); null links the only player of a solo game, and nobody in a
 *     multiplayer game. Ignored unless a valid {@code X-Profile-Token} accompanies the request.
 */
public record CreateGameRequest(
        @NotNull GameMode mode,
        @NotEmpty @Size(max = 8) List<@NotEmpty @Size(max = 60) String> playerNames,
        @NotNull @Valid GameSettingsRequest settings,
        @Min(0) Integer profilePlayerIndex) {

    public CreateGameRequest(GameMode mode, List<String> playerNames, GameSettingsRequest settings) {
        this(mode, playerNames, settings, null);
    }
}
