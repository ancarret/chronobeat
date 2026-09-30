package com.chronobeat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.chronobeat.config.GameProperties;
import com.chronobeat.domain.Difficulty;
import com.chronobeat.domain.Game;
import com.chronobeat.domain.GameMode;
import com.chronobeat.domain.GamePlayer;
import com.chronobeat.domain.GameSettings;
import com.chronobeat.domain.MusicGenre;
import com.chronobeat.domain.MusicProviderType;
import com.chronobeat.domain.Song;
import com.chronobeat.domain.TimelineEntry;
import com.chronobeat.exception.InsufficientCatalogException;
import com.chronobeat.repository.GameRoundRepository;
import com.chronobeat.repository.SongRepository;
import com.chronobeat.util.RandomProvider;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class SongSelectionServiceTest {

    @Mock
    private SongRepository songRepository;

    @Mock
    private GameRoundRepository gameRoundRepository;

    private SongSelectionService service;

    /** Always returns the first element, making pool ordering assertions deterministic. */
    private static final RandomProvider FIRST_ELEMENT_PICKER = new RandomProvider() {
        @Override
        public int nextInt(int bound) {
            return 0;
        }

        @Override
        public <T> T pick(List<T> items) {
            return items.get(0);
        }
    };

    @BeforeEach
    void setUp() {
        GameProperties properties = new GameProperties();
        properties.setRecentArtistAvoidanceWindow(3);
        service = new SongSelectionService(songRepository, gameRoundRepository, FIRST_ELEMENT_PICKER, properties);
    }

    private Game gameWithSettings(Difficulty difficulty) {
        GameSettings settings = new GameSettings(null, null, null, null, difficulty, 3, null);
        return new Game(GameMode.SOLO, settings);
    }

    private Song song(String artist, int year) {
        return new Song(
                MusicProviderType.APPLE_MUSIC, UUID.randomUUID().toString(), artist + " Song " + year, artist, "Album",
                LocalDate.of(year, 1, 1), year, MusicGenre.ROCK, "Rock", "US", null, "https://preview", null);
    }

    @Test
    void throwsWhenNoCandidatesMatchFilters() {
        Game game = gameWithSettings(Difficulty.NORMAL);
        GamePlayer player = new GamePlayer("P1", 0, 3);
        game.addPlayer(player);
        when(songRepository.findAll(any(Specification.class))).thenReturn(List.of());
        when(gameRoundRepository.findUsedSongIdsByGameId(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.selectNextSong(game, player)).isInstanceOf(InsufficientCatalogException.class);
    }

    @Test
    void avoidsMostRecentArtistWhenAlternativeExists() {
        Game game = gameWithSettings(Difficulty.NORMAL);
        GamePlayer player = new GamePlayer("P1", 0, 3);
        game.addPlayer(player);

        Song recentArtistSong = song("Queen", 1980);
        Song otherSong = song("Abba", 1979);
        when(songRepository.findAll(any(Specification.class))).thenReturn(List.of(recentArtistSong, otherSong));
        when(gameRoundRepository.findUsedSongIdsByGameId(any())).thenReturn(List.of());
        when(gameRoundRepository.findArtistHistoryByGamePlayerId(any())).thenReturn(List.of("Queen"));

        Song selected = service.selectNextSong(game, player);

        assertThat(selected.getArtist()).isEqualTo("Abba");
    }

    @Test
    void fallsBackToRecentArtistWhenItIsTheOnlyCandidate() {
        Game game = gameWithSettings(Difficulty.NORMAL);
        GamePlayer player = new GamePlayer("P1", 0, 3);
        game.addPlayer(player);

        Song onlyOption = song("Queen", 1980);
        when(songRepository.findAll(any(Specification.class))).thenReturn(List.of(onlyOption));
        when(gameRoundRepository.findUsedSongIdsByGameId(any())).thenReturn(List.of());
        when(gameRoundRepository.findArtistHistoryByGamePlayerId(any())).thenReturn(List.of("Queen"));

        Song selected = service.selectNextSong(game, player);

        assertThat(selected).isEqualTo(onlyOption);
    }

    @Test
    void easyDifficultyPrefersTheCandidateFurthestFromExistingTimelineYears() {
        Game game = gameWithSettings(Difficulty.EASY);
        GamePlayer player = new GamePlayer("P1", 0, 3);
        game.addPlayer(player);
        player.addToTimeline(new TimelineEntry(song("Nirvana", 1991), 0, 1));

        Song farAway = song("Beatles", 1965); // gap of 26 years
        Song closeBy = song("Coldplay", 1995); // gap of 4 years
        when(songRepository.findAll(any(Specification.class))).thenReturn(List.of(closeBy, farAway));
        when(gameRoundRepository.findUsedSongIdsByGameId(any())).thenReturn(List.of());
        when(gameRoundRepository.findArtistHistoryByGamePlayerId(any())).thenReturn(List.of());

        Song selected = service.selectNextSong(game, player);

        assertThat(selected).isEqualTo(farAway);
    }

    @Test
    void hardDifficultyPrefersTheCandidateClosestToExistingTimelineYears() {
        Game game = gameWithSettings(Difficulty.HARD);
        GamePlayer player = new GamePlayer("P1", 0, 3);
        game.addPlayer(player);
        player.addToTimeline(new TimelineEntry(song("Nirvana", 1991), 0, 1));

        Song farAway = song("Beatles", 1965);
        Song closeBy = song("Coldplay", 1995);
        when(songRepository.findAll(any(Specification.class))).thenReturn(List.of(farAway, closeBy));
        when(gameRoundRepository.findUsedSongIdsByGameId(any())).thenReturn(List.of());
        when(gameRoundRepository.findArtistHistoryByGamePlayerId(any())).thenReturn(List.of());

        Song selected = service.selectNextSong(game, player);

        assertThat(selected).isEqualTo(closeBy);
    }

    @Test
    void sharedSongAvoidsArtistsRecentlyPlayedAnywhereAtTheTable() {
        Game game = gameWithSettings(Difficulty.NORMAL);
        game.addPlayer(new GamePlayer("P1", 0, 3));
        game.addPlayer(new GamePlayer("P2", 1, 3));

        Song recentArtistSong = song("Queen", 1980);
        Song otherSong = song("Abba", 1979);
        when(songRepository.findAll(any(Specification.class))).thenReturn(List.of(recentArtistSong, otherSong));
        when(gameRoundRepository.findUsedSongIdsByGameId(any())).thenReturn(List.of());
        // One shared round is stored once per player, so the same artist shows up twice in the history.
        when(gameRoundRepository.findArtistHistoryByGameId(any())).thenReturn(List.of("Queen", "Queen"));

        assertThat(service.selectSharedSong(game).getArtist()).isEqualTo("Abba");
    }

    @Test
    void sharedSongDifficultyIsMeasuredAgainstEveryonesTimeline() {
        Game game = gameWithSettings(Difficulty.HARD);
        GamePlayer p1 = new GamePlayer("P1", 0, 3);
        GamePlayer p2 = new GamePlayer("P2", 1, 3);
        game.addPlayer(p1);
        game.addPlayer(p2);
        p1.addToTimeline(new TimelineEntry(song("Nirvana", 1991), 0, 1));
        p2.addToTimeline(new TimelineEntry(song("Beatles", 1965), 0, 1));

        Song nearSecondPlayersCard = song("Kinks", 1966);
        Song nearNobody = song("Daft Punk", 1978);
        when(songRepository.findAll(any(Specification.class))).thenReturn(List.of(nearNobody, nearSecondPlayersCard));
        when(gameRoundRepository.findUsedSongIdsByGameId(any())).thenReturn(List.of());
        when(gameRoundRepository.findArtistHistoryByGameId(any())).thenReturn(List.of());

        // Hard means a close call for somebody: 1966 sits right next to P2's 1965.
        assertThat(service.selectSharedSong(game)).isEqualTo(nearSecondPlayersCard);
    }
}
