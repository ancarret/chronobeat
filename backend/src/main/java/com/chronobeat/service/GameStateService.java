package com.chronobeat.service;

import com.chronobeat.domain.Game;
import com.chronobeat.domain.GamePlayer;
import com.chronobeat.domain.GameRound;
import com.chronobeat.domain.RoundStatus;
import com.chronobeat.dto.game.GamePhase;
import com.chronobeat.dto.game.GameResponse;
import com.chronobeat.dto.game.GameStateResponse;
import com.chronobeat.dto.game.RoundPendingResponse;
import com.chronobeat.dto.game.RoundSummaryResponse;
import com.chronobeat.event.PresenceRegistry;
import com.chronobeat.exception.GameNotFoundException;
import com.chronobeat.exception.InvalidGameStateException;
import com.chronobeat.exception.NotYourRoundException;
import com.chronobeat.mapper.GameMapper;
import com.chronobeat.repository.GameRepository;
import com.chronobeat.repository.GameRoundRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read model behind every game screen: given a game and a player, says which phase they are in
 * and hands over exactly what that phase needs. One code path serves solo, shared-device and online
 * games, turn-based and shared-song alike.
 *
 * <p>It only ever exposes what the phase allows &mdash; the mystery song's identity appears solely in
 * {@code REVEAL}/{@code FINISHED} summaries, which are built from rounds that are already resolved.
 */
@Service
@Transactional(readOnly = true)
public class GameStateService {

    private final GameRepository gameRepository;
    private final GameRoundRepository gameRoundRepository;
    private final GameMapper gameMapper;
    private final PlayerAuthenticator playerAuthenticator;
    private final PresenceRegistry presence;

    public GameStateService(
            GameRepository gameRepository,
            GameRoundRepository gameRoundRepository,
            GameMapper gameMapper,
            PlayerAuthenticator playerAuthenticator,
            PresenceRegistry presence) {
        this.gameRepository = gameRepository;
        this.gameRoundRepository = gameRoundRepository;
        this.gameMapper = gameMapper;
        this.playerAuthenticator = playerAuthenticator;
        this.presence = presence;
    }

    /**
     * @param requestedPlayerId whose view to build on a shared device; null lets the server pick the next
     *     player who has something to do. Ignored for online games, where the token decides.
     * @param playerToken the caller's token; required for online games
     */
    public GameStateResponse stateFor(UUID gameId, UUID requestedPlayerId, String playerToken) {
        Game game = gameRepository.findWithPlayersById(gameId).orElseThrow(() -> new GameNotFoundException(gameId));
        Optional<GamePlayer> viewer = resolveViewer(game, requestedPlayerId, playerToken);
        List<UUID> connected = List.copyOf(presence.connectedPlayers(gameId));

        return switch (game.getStatus()) {
            case CREATED -> build(game, Set.of(), GamePhase.LOBBY, viewer.orElse(null), null, null, List.of(), null, connected);
            case FINISHED -> {
                RoundSummaryResponse last = lastSummary(game);
                yield build(game, Set.of(), GamePhase.FINISHED, viewer.orElse(null), null, last, List.of(), null, connected);
            }
            case ACTIVE -> activeState(game, viewer, connected);
        };
    }

    private GameStateResponse activeState(Game game, Optional<GamePlayer> viewer, List<UUID> connected) {
        List<GameRound> table = gameRoundRepository.findRound(game.getId(), game.getCurrentRoundNumber());
        Set<UUID> answered = table.stream()
                .filter(r -> r.getStatus() == RoundStatus.LOCKED)
                .map(r -> r.getGamePlayer().getId())
                .collect(Collectors.toSet());
        List<GameRound> pending = table.stream().filter(r -> r.getStatus() == RoundStatus.PENDING).toList();

        if (!table.isEmpty() && pending.isEmpty() && table.stream().allMatch(r -> r.getStatus() == RoundStatus.RESOLVED)) {
            return build(game, Set.of(), GamePhase.REVEAL, viewer.orElse(null), null, summaryOf(game, table), List.of(), null, connected);
        }

        // On a shared device nobody chose a viewer: show whoever still has to answer, in seating order.
        GamePlayer who = viewer.orElseGet(() -> pending.stream().map(GameRound::getGamePlayer).findFirst().orElse(null));
        Optional<GameRound> mine = who == null
                ? Optional.empty()
                : pending.stream().filter(r -> r.getGamePlayer().equals(who)).findFirst();

        if (mine.isPresent()) {
            RoundPendingResponse round = gameMapper.toRoundPendingResponse(mine.get());
            return build(game, answered, GamePhase.ANSWERING, who, round, null, List.of(), round.answerSecondsRemaining(), connected);
        }

        List<UUID> waitingOn = pending.stream().map(r -> r.getGamePlayer().getId()).toList();
        Integer remaining = pending.stream()
                .map(gameMapper::secondsRemaining)
                .filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        return build(game, answered, GamePhase.WAITING, who, null, null, waitingOn, remaining, connected);
    }

    private GameStateResponse build(
            Game game,
            Set<UUID> answered,
            GamePhase phase,
            GamePlayer viewer,
            RoundPendingResponse round,
            RoundSummaryResponse summary,
            List<UUID> waitingOn,
            Integer secondsRemaining,
            List<UUID> connected) {
        GameResponse dto = gameMapper.toGameResponse(game, answered);
        return new GameStateResponse(
                dto, phase, viewer == null ? null : viewer.getId(), round, summary, waitingOn, secondsRemaining, connected);
    }

    private Optional<GamePlayer> resolveViewer(Game game, UUID requestedPlayerId, String playerToken) {
        Optional<GamePlayer> authenticated = playerAuthenticator.authenticate(game, playerToken);
        if (authenticated.isPresent()) {
            if (requestedPlayerId != null && !authenticated.get().getId().equals(requestedPlayerId)) {
                throw new NotYourRoundException("That view belongs to another player");
            }
            return authenticated;
        }
        if (requestedPlayerId == null) {
            return Optional.empty();
        }
        return Optional.of(game.getPlayers().stream()
                .filter(p -> p.getId().equals(requestedPlayerId))
                .findFirst()
                .orElseThrow(() -> new InvalidGameStateException("Player " + requestedPlayerId + " is not part of game " + game.getId())));
    }

    private RoundSummaryResponse lastSummary(Game game) {
        if (game.getCurrentRoundNumber() == 0) {
            return null;
        }
        List<GameRound> table = gameRoundRepository.findRound(game.getId(), game.getCurrentRoundNumber());
        boolean resolved = !table.isEmpty() && table.stream().allMatch(r -> r.getStatus() == RoundStatus.RESOLVED);
        return resolved ? summaryOf(game, table) : null;
    }

    private RoundSummaryResponse summaryOf(Game game, List<GameRound> resolvedTable) {
        return new RoundSummaryResponse(
                resolvedTable.get(0).getRoundNumber(),
                gameMapper.toSongRevealResponse(resolvedTable.get(0).getSong()),
                resolvedTable.stream().map(r -> gameMapper.toRoundResult(r, game.getStatus())).toList());
    }
}
