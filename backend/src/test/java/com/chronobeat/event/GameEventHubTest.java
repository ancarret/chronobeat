package com.chronobeat.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class GameEventHubTest {

    private final UUID game = UUID.randomUUID();
    private final UUID ana = UUID.randomUUID();
    private final UUID bea = UUID.randomUUID();

    private PresenceRegistry presence;
    private GameEventHub hub;

    @BeforeEach
    void setUp() {
        presence = new PresenceRegistry();
        // Events published here go nowhere: these tests are about bookkeeping, not delivery.
        ApplicationEventPublisher nowhere = event -> {};
        hub = new GameEventHub(presence, new GameEvents(nowhere));
    }

    @Test
    void openingAStreamMarksThePlayerConnectedAndClosingItMarksThemGone() {
        GameEventHub.Subscription stream = hub.open(game, ana);
        assertThat(presence.connectedPlayers(game)).containsExactly(ana);
        assertThat(hub.subscriberCount(game)).isEqualTo(1);

        hub.close(game, stream);

        assertThat(presence.connectedPlayers(game)).isEmpty();
        assertThat(hub.subscriberCount(game)).isZero();
    }

    @Test
    void reportingTheSameDeadStreamRepeatedlyDoesNotEraseTheNewOne() {
        // A browser moves from the lobby to the game: it opens a new stream while the old one is still
        // registered. The old stream is then reported dead through more than one path (error + completion).
        GameEventHub.Subscription lobbyStream = hub.open(game, ana);
        hub.open(game, ana);

        hub.close(game, lobbyStream);
        hub.close(game, lobbyStream);
        hub.close(game, lobbyStream);

        assertThat(presence.connectedPlayers(game)).as("the newer stream is still open").containsExactly(ana);
        assertThat(hub.subscriberCount(game)).isEqualTo(1);
    }

    @Test
    void aPlayerStaysConnectedWhileAnyOfTheirStreamsIsOpen() {
        GameEventHub.Subscription first = hub.open(game, ana);
        GameEventHub.Subscription second = hub.open(game, ana);

        hub.close(game, first);
        assertThat(presence.connectedPlayers(game)).containsExactly(ana);

        hub.close(game, second);
        assertThat(presence.connectedPlayers(game)).isEmpty();
    }

    @Test
    void playersAreTrackedIndependentlyAndSpectatorsDoNotCount() {
        hub.open(game, ana);
        GameEventHub.Subscription beaStream = hub.open(game, bea);
        hub.open(game, null); // a spectator has no seat

        assertThat(presence.connectedPlayers(game)).containsExactlyInAnyOrder(ana, bea);
        assertThat(hub.subscriberCount(game)).isEqualTo(3);

        hub.close(game, beaStream);
        assertThat(presence.connectedPlayers(game)).containsExactly(ana);
    }

    @Test
    void presenceChangesAreAnnouncedOnlyWhenSomeoneActuallyAppearsOrDisappears() {
        java.util.List<GameEventType> announced = new java.util.ArrayList<>();
        ApplicationEventPublisher recorder = event -> announced.add(((GameChangedEvent) event).type());
        GameEventHub recording = new GameEventHub(new PresenceRegistry(), new GameEvents(recorder));

        GameEventHub.Subscription first = recording.open(game, ana);
        GameEventHub.Subscription second = recording.open(game, ana); // already here: nothing new to announce
        assertThat(announced).containsExactly(GameEventType.PRESENCE_CHANGED);

        recording.close(game, first); // still has the second stream
        assertThat(announced).hasSize(1);

        recording.close(game, second);
        assertThat(announced).containsExactly(GameEventType.PRESENCE_CHANGED, GameEventType.PRESENCE_CHANGED);
    }
}
