package com.chronobeat.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.chronobeat.TestcontainersConfiguration;
import com.chronobeat.domain.MusicGenre;
import com.chronobeat.domain.MusicProviderType;
import com.chronobeat.domain.Song;
import com.chronobeat.event.GameEventHub;
import com.chronobeat.repository.GameRepository;
import com.chronobeat.repository.GameRoundRepository;
import com.chronobeat.repository.ProfileRepository;
import com.chronobeat.repository.SongRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The online flow over real HTTP: JSON contracts, the token headers, error statuses and a live
 * Server-Sent Events stream. Complements the service-level tests by exercising what a browser would.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OnlineHttpFlowTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper json;

    @Autowired
    private GameEventHub eventHub;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private GameRoundRepository gameRoundRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private SongRepository songRepository;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void seed() {
        gameRepository.deleteAll();
        profileRepository.deleteAll();
        songRepository.deleteAll();
        songRepository.saveAll(List.of(
                song("Fleetwood Mac", "Dreams", 1977),
                song("Nirvana", "Smells Like Teen Spirit", 1991),
                song("Oasis", "Wonderwall", 1995),
                song("Coldplay", "Viva La Vida", 2008)));
    }

    /** The preview URL is opaque on purpose: if it echoed the title, the no-leak assertions below would prove nothing. */
    private Song song(String artist, String title, int year) {
        return new Song(
                MusicProviderType.APPLE_MUSIC, UUID.randomUUID().toString(), title, artist, "Album",
                LocalDate.of(year, 6, 1), year, MusicGenre.POP, "Pop", "US", null, "https://preview.example/" + UUID.randomUUID(), 200000);
    }

    private HttpResponse<String> call(String method, String path, String playerToken, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (playerToken != null) {
            request.header("X-Player-Token", playerToken);
        }
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode ok(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
        return response.body().isBlank() ? null : json.readTree(response.body());
    }

    /** Reads the SSE stream on a background thread into a queue of its data/event lines. */
    private static final class EventStream {
        final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        final AtomicReference<Stream<String>> body = new AtomicReference<>();
        CompletableFuture<?> connection;

        /** Hangs up like a closed browser tab: closing the body stream drops the connection. */
        void close() {
            Stream<String> open = body.get();
            if (open != null) {
                open.close();
            }
        }

        String next(long seconds) throws InterruptedException {
            return lines.poll(seconds, TimeUnit.SECONDS);
        }

        /** Waits for a `changed` event of the given type, ignoring others. */
        boolean awaitChanged(String type, long seconds) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
            while (System.nanoTime() < deadline) {
                String line = lines.poll(200, TimeUnit.MILLISECONDS);
                if (line != null && line.contains("\"type\":\"" + type + "\"")) {
                    return true;
                }
            }
            return false;
        }
    }

    private EventStream openEvents(String gameId, String playerId) {
        EventStream stream = new EventStream();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/games/" + gameId + "/events?playerId=" + playerId))
                .header("Accept", "text/event-stream")
                .timeout(Duration.ofMinutes(1))
                .build();
        stream.connection = http.sendAsync(request, HttpResponse.BodyHandlers.ofLines()).thenAccept(response -> {
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("text/event-stream");
            stream.body.set(response.body());
            response.body().forEach(stream.lines::add);
        });
        return stream;
    }

    @Test
    void aWholeRoomLifecycleWorksOverHttpWithLiveEvents() throws Exception {
        // --- Ana opens a room
        String settings = "{\"difficulty\":\"NORMAL\",\"maxLives\":3,\"playStyle\":\"SHARED_SONGS\"}";
        JsonNode created = ok(call("POST", "/api/rooms", null, "{\"nickname\":\"Ana\",\"settings\":" + settings + "}"));
        String gameId = created.get("gameId").asString();
        String code = created.get("roomCode").asString();
        String anaId = created.get("playerId").asString();
        String anaToken = created.get("playerToken").asString();
        assertThat(created.get("game").get("settings").get("playStyle").asString()).isEqualTo("SHARED_SONGS");

        // --- and starts listening
        EventStream events = openEvents(gameId, anaId);
        try {
            assertThat(events.next(10)).isEqualTo("event:hello");
            assertThat(events.awaitChanged("PRESENCE_CHANGED", 10)).as("Ana's own connection is announced").isTrue();

            JsonNode lobby = ok(call("GET", "/api/games/" + gameId + "/state", anaToken, null));
            assertThat(lobby.get("phase").asString()).isEqualTo("LOBBY");
            assertThat(lobby.get("connectedPlayerIds")).extracting(JsonNode::asString).containsExactly(anaId);

            // --- Bea previews the room, then joins: Ana is told
            JsonNode preview = ok(call("GET", "/api/rooms/" + code.toLowerCase(), null, null));
            assertThat(preview.get("hostName").asString()).isEqualTo("Ana");
            JsonNode joined = ok(call("POST", "/api/rooms/" + code + "/join", null, "{\"nickname\":\"Bea\"}"));
            String beaToken = joined.get("playerToken").asString();
            assertThat(events.awaitChanged("LOBBY_CHANGED", 10)).isTrue();

            // --- only the host can start
            assertThat(call("POST", "/api/games/" + gameId + "/start", beaToken, null).statusCode()).isEqualTo(403);
            assertThat(call("POST", "/api/games/" + gameId + "/start", null, null).statusCode()).isEqualTo(401);
            ok(call("POST", "/api/games/" + gameId + "/start", anaToken, null));
            assertThat(events.awaitChanged("GAME_STARTED", 10)).isTrue();

            // --- the pending round never carries the answer
            String mysteryTitle = gameRoundRepository
                    .findRound(UUID.fromString(gameId), 1).get(0).getSong().getTitle();
            HttpResponse<String> anaStateResponse = call("GET", "/api/games/" + gameId + "/state", anaToken, null);
            JsonNode anaState = ok(anaStateResponse);
            assertThat(anaState.get("phase").asString()).isEqualTo("ANSWERING");
            assertThat(anaState.get("round").get("previewUrl").asString()).startsWith("https://preview.example/");
            assertThat(anaStateResponse.body()).doesNotContain(mysteryTitle);
            assertThat(anaState.get("round").has("title")).isFalse();

            // --- bad credentials are rejected with JSON errors
            HttpResponse<String> forged = call("POST", "/api/games/" + gameId + "/rounds/" + anaState.get("round").get("roundId").asString() + "/answer", "forged", "{}");
            assertThat(forged.statusCode()).isEqualTo(401);
            assertThat(json.readTree(forged.body()).get("message").asString()).contains("X-Player-Token");
            assertThat(call("GET", "/api/games/" + gameId + "/state", null, null).statusCode()).isEqualTo(401);

            // --- both lock in; the last answer resolves the round and everyone hears about it
            String anaRound = anaState.get("round").get("roundId").asString();
            JsonNode first = ok(call("POST", "/api/games/" + gameId + "/rounds/" + anaRound + "/answer", anaToken, "{}"));
            assertThat(first.get("resolved").asBoolean()).isFalse();
            assertThat(first.get("waitingFor").asInt()).isEqualTo(1);
            assertThat(first.get("result").isNull()).isTrue();

            JsonNode beaState = ok(call("GET", "/api/games/" + gameId + "/state", beaToken, null));
            String beaRound = beaState.get("round").get("roundId").asString();
            JsonNode second = ok(call("POST", "/api/games/" + gameId + "/rounds/" + beaRound + "/answer", beaToken, "{}"));
            assertThat(second.get("resolved").asBoolean()).isTrue();
            assertThat(second.get("result").get("reveal").get("title").asString()).isEqualTo(mysteryTitle);
            assertThat(events.awaitChanged("ROUND_RESOLVED", 10)).isTrue();

            JsonNode reveal = ok(call("GET", "/api/games/" + gameId + "/state", anaToken, null));
            assertThat(reveal.get("phase").asString()).isEqualTo("REVEAL");
            assertThat(reveal.get("summary").get("results")).hasSize(2);
        } finally {
            events.close();
        }

        // --- a hung-up tab is only noticed when the server next writes to it, which the periodic heartbeat
        // does in production; the scheduler is off in tests, so run it by hand.
        JsonNode after = null;
        for (int i = 0; i < 50; i++) {
            eventHub.heartbeat();
            after = ok(call("GET", "/api/games/" + gameId + "/state", anaToken, null));
            if (after.get("connectedPlayerIds").isEmpty()) {
                break;
            }
            Thread.sleep(100);
        }
        assertThat(after.get("connectedPlayerIds")).as("Ana hung up").isEmpty();
    }

    @Test
    void errorsAreReportedAsJsonWithSensibleStatuses() throws Exception {
        assertThat(call("GET", "/api/rooms/NOPE", null, null).statusCode()).isEqualTo(404);
        assertThat(call("POST", "/api/rooms", null, "{\"nickname\":\"\",\"settings\":{\"difficulty\":\"NORMAL\"}}").statusCode()).isEqualTo(400);
        HttpResponse<String> noSuchStream = call("GET", "/api/games/" + UUID.randomUUID() + "/events", null, null);
        assertThat(noSuchStream.statusCode()).isEqualTo(404);
        assertThat(json.readTree(noSuchStream.body()).get("message").asString()).contains("Game not found");

        // Even a browser's EventSource (which only accepts text/event-stream) gets a readable JSON error.
        HttpResponse<String> asEventSource = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/games/" + UUID.randomUUID() + "/events"))
                        .header("Accept", "text/event-stream").build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(asEventSource.statusCode()).isEqualTo(404);
        assertThat(asEventSource.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        assertThat(call("POST", "/api/rooms", null,
                "{\"nickname\":\"Ana\",\"settings\":{\"difficulty\":\"NORMAL\",\"targetTimelineSize\":1}}").statusCode())
                .as("a race to fewer than 2 cards makes no sense")
                .isEqualTo(400);
    }
}
