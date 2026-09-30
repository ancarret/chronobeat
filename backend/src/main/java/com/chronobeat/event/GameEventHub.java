package com.chronobeat.event;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Fans game changes out to the browsers watching that game over Server-Sent Events.
 *
 * <p>Events carry no game data, only "something changed": clients respond by re-fetching their own
 * state, so the stream can be lossy or reordered without ever putting a screen out of step. That is
 * also why a plain one-way stream is enough here, with no need for a bidirectional protocol: actions go
 * up as ordinary REST calls, and the browser's {@code EventSource} reconnects by itself.
 *
 * <p>State is in memory, so this assumes a single backend instance (fine for one Render service). Running
 * several would need a shared broker (Redis pub/sub, Postgres LISTEN/NOTIFY) behind {@link #publishLocally}.
 */
@Component
public class GameEventHub {

    private static final Logger log = LoggerFactory.getLogger(GameEventHub.class);

    /** Browsers reconnect on their own, so a generous ceiling just bounds how long a dead socket can linger. */
    private static final long STREAM_TIMEOUT_MILLIS = 15 * 60 * 1000L;

    private final PresenceRegistry presence;
    private final GameEvents gameEvents;

    /** Sends run off the publishing thread so one slow client can't hold up a game transaction's thread. */
    private final ExecutorService sender = Executors.newVirtualThreadPerTaskExecutor();

    private final Map<UUID, Set<Subscription>> subscriptions = new ConcurrentHashMap<>();

    private record Subscription(SseEmitter emitter, UUID playerId) {}

    public GameEventHub(PresenceRegistry presence, GameEvents gameEvents) {
        this.presence = presence;
        this.gameEvents = gameEvents;
    }

    /**
     * Opens an event stream for a game.
     *
     * @param playerId the online player watching, used only for the "connected" indicator; null for spectators
     */
    public SseEmitter subscribe(UUID gameId, UUID playerId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        Subscription subscription = new Subscription(emitter, playerId);
        subscriptions.computeIfAbsent(gameId, id -> new CopyOnWriteArraySet<>()).add(subscription);

        Runnable cleanup = () -> unsubscribe(gameId, subscription);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> cleanup.run());

        // The first event makes the response start streaming and tells the client it is connected. Clients
        // re-fetch their state on it, which closes the gap between their last fetch and this stream opening.
        send(subscription, "hello", "{}");
        if (playerId != null && presence.opened(gameId, playerId)) {
            gameEvents.publish(gameId, GameEventType.PRESENCE_CHANGED);
        }
        return emitter;
    }

    private void unsubscribe(UUID gameId, Subscription subscription) {
        Set<Subscription> forGame = subscriptions.get(gameId);
        if (forGame != null) {
            forGame.remove(subscription);
            if (forGame.isEmpty()) {
                subscriptions.remove(gameId, forGame);
            }
        }
        if (subscription.playerId() != null && presence.closed(gameId, subscription.playerId())) {
            gameEvents.publish(gameId, GameEventType.PRESENCE_CHANGED);
        }
    }

    /** Runs once the publishing transaction committed (or immediately if there was none). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onGameChanged(GameChangedEvent event) {
        publishLocally(event);
    }

    private void publishLocally(GameChangedEvent event) {
        Set<Subscription> forGame = subscriptions.get(event.gameId());
        if (forGame == null) {
            return;
        }
        String payload = "{\"type\":\"" + event.type().name() + "\"}";
        for (Subscription subscription : forGame) {
            sender.execute(() -> send(subscription, "changed", payload));
        }
    }

    /** Proxies and load balancers drop connections that stay silent for a minute or so. */
    @Scheduled(fixedRate = 20_000)
    public void heartbeat() {
        subscriptions.values().forEach(forGame -> forGame.forEach(subscription -> sender.execute(() -> {
            try {
                subscription.emitter().send(SseEmitter.event().comment("keep-alive"));
            } catch (IOException | IllegalStateException e) {
                subscription.emitter().complete();
            }
        })));
    }

    private void send(Subscription subscription, String name, String data) {
        try {
            subscription.emitter().send(SseEmitter.event().name(name).data(data));
        } catch (IOException | IllegalStateException e) {
            // The client went away between events; onCompletion/onError does the cleanup.
            log.debug("Dropping closed event stream: {}", e.getMessage());
            subscription.emitter().complete();
        }
    }

    /** Test/diagnostic hook: how many streams are open for a game. */
    public int subscriberCount(UUID gameId) {
        Set<Subscription> forGame = subscriptions.get(gameId);
        return forGame == null ? 0 : forGame.size();
    }
}
