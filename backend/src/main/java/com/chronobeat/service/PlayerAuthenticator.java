package com.chronobeat.service;

import com.chronobeat.domain.Game;
import com.chronobeat.domain.GamePlayer;
import com.chronobeat.domain.GameRound;
import com.chronobeat.exception.InvalidPlayerTokenException;
import com.chronobeat.exception.NotYourRoundException;
import com.chronobeat.util.SecretTokens;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Who is allowed to act in a game. Shared-device games trust whoever holds the device, exactly as
 * before. Online games hand every player a secret token at join time; without it nobody can answer
 * for someone else, start the game or skip a reveal, even knowing the (unguessable) game id.
 */
@Component
public class PlayerAuthenticator {

    /** The acting player for an online game; empty for shared-device games, which need no token. */
    public Optional<GamePlayer> authenticate(Game game, String token) {
        if (!game.isOnline()) {
            return Optional.empty();
        }
        if (token == null || token.isBlank()) {
            throw new InvalidPlayerTokenException();
        }
        String hash = SecretTokens.hash(token.trim());
        return Optional.of(game.getPlayers().stream()
                .filter(p -> hash.equals(p.getTokenHash()))
                .findFirst()
                .orElseThrow(InvalidPlayerTokenException::new));
    }

    public void requireOwnRound(Game game, GameRound round, String token) {
        authenticate(game, token).ifPresent(player -> {
            if (!player.equals(round.getGamePlayer())) {
                throw new NotYourRoundException();
            }
        });
    }

    /** Only the room's host may start the game; a no-op on shared-device games. */
    public void requireHost(Game game, String token) {
        authenticate(game, token).ifPresent(player -> {
            if (!player.isHost()) {
                throw new NotYourRoundException("Only the host can do that");
            }
        });
    }
}
