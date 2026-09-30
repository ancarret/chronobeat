package com.chronobeat.event;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Which online players currently hold an open event stream. Purely in-memory and best-effort: it feeds
 * the "connected" dot in the lobby and never influences game rules, so losing it on a restart is harmless
 * (streams reconnect and re-register themselves).
 */
@Component
public class PresenceRegistry {

    /** game -> player -> number of open streams (a player may briefly have two while a tab reconnects). */
    private final Map<UUID, Map<UUID, Integer>> streams = new ConcurrentHashMap<>();

    /** @return true if this made the player newly connected */
    public boolean opened(UUID gameId, UUID playerId) {
        Map<UUID, Integer> players = streams.computeIfAbsent(gameId, id -> new ConcurrentHashMap<>());
        return players.merge(playerId, 1, Integer::sum) == 1;
    }

    /** @return true if this left the player with no open stream */
    public boolean closed(UUID gameId, UUID playerId) {
        Map<UUID, Integer> players = streams.get(gameId);
        if (players == null) {
            return false;
        }
        boolean[] gone = {false};
        players.computeIfPresent(playerId, (id, count) -> {
            if (count <= 1) {
                gone[0] = true;
                return null;
            }
            return count - 1;
        });
        if (players.isEmpty()) {
            streams.remove(gameId, players);
        }
        return gone[0];
    }

    public Set<UUID> connectedPlayers(UUID gameId) {
        Map<UUID, Integer> players = streams.get(gameId);
        return players == null ? Set.of() : players.keySet().stream().collect(Collectors.toUnmodifiableSet());
    }
}
