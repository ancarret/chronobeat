package com.chronobeat.event;

import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Announces game changes. Listeners are bound to the <em>commit</em> of the publishing transaction
 * (see {@code GameEventHub}), so a client woken by an event always finds the new state when it asks.
 */
@Component
public class GameEvents {

    private final ApplicationEventPublisher publisher;

    public GameEvents(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publish(UUID gameId, GameEventType type) {
        publisher.publishEvent(new GameChangedEvent(gameId, type));
    }
}
