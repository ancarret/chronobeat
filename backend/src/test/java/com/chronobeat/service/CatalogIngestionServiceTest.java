package com.chronobeat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronobeat.domain.MusicGenre;
import com.chronobeat.domain.MusicProviderType;
import com.chronobeat.domain.Song;
import com.chronobeat.dto.song.IngestionResult;
import com.chronobeat.integration.music.MusicProvider;
import com.chronobeat.integration.music.ProviderSearchQuery;
import com.chronobeat.integration.music.ProviderTrack;
import com.chronobeat.repository.SongRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class CatalogIngestionServiceTest {

    @Mock
    private MusicProvider musicProvider;

    @Mock
    private SongRepository songRepository;

    private CatalogIngestionService service;

    @BeforeEach
    void setUp() {
        service = new CatalogIngestionService(musicProvider, songRepository, new GenreNormalizer());
    }

    @ParameterizedTest(name = "\"{0}\" normalizes to \"{1}\"")
    @CsvSource({
        "Wonderwall - Remastered 2014, wonderwall",
        "Wonderwall (Remastered), wonderwall",
        "Billie Jean - Single Version, billie jean",
        "Africa (Live), africa",
        "Bohemian Rhapsody, bohemian rhapsody"
    })
    void normalizeTitleStripsKnownEditionSuffixes(String raw, String expected) {
        assertThat(CatalogIngestionService.normalizeTitle(raw)).isEqualTo(expected);
    }

    @Test
    void normalizeTitleDoesNotTouchALeadingParentheticalThatIsPartOfTheRealTitle() {
        // "(Sittin' On) The Dock of the Bay" - the suffix pattern only matches at the END of the string.
        String title = "(Sittin' On) The Dock of the Bay";
        assertThat(CatalogIngestionService.normalizeTitle(title)).isEqualTo(title.toLowerCase());
    }

    @Test
    void tracksWithKaraokeInTitleAreFilteredOut() {
        ProviderTrack karaoke = track("1", "Wonderwall (Karaoke Version)", "Karaoke Band", 1995);
        when(musicProvider.search(any(ProviderSearchQuery.class))).thenReturn(List.of(karaoke));

        IngestionResult result = service.ingestQuery("wonderwall", "US");

        assertThat(result.filteredOut()).isEqualTo(1);
        assertThat(result.added()).isZero();
        verify(songRepository, never()).save(any());
    }

    @Test
    void duplicateExternalIdIsSkipped() {
        when(musicProvider.getType()).thenReturn(MusicProviderType.APPLE_MUSIC);
        ProviderTrack existingTrack = track("42", "Wonderwall", "Oasis", 1995);
        when(musicProvider.search(any(ProviderSearchQuery.class))).thenReturn(List.of(existingTrack));
        Song existingSong = new Song(
                MusicProviderType.APPLE_MUSIC, "42", "Wonderwall", "Oasis", "(What's the Story) Morning Glory?",
                LocalDate.of(1995, 10, 2), 1995, MusicGenre.ROCK, "Rock", "US", null, "https://preview", null);
        when(songRepository.findByProviderAndExternalId(MusicProviderType.APPLE_MUSIC, "42")).thenReturn(Optional.of(existingSong));

        IngestionResult result = service.ingestQuery("wonderwall", "US");

        assertThat(result.deduplicated()).isEqualTo(1);
        assertThat(result.added()).isZero();
        verify(songRepository, never()).save(any());
    }

    @Test
    void remasterOfAlreadyIngestedSongUpdatesCanonicalYearInsteadOfDuplicating() {
        when(musicProvider.getType()).thenReturn(MusicProviderType.APPLE_MUSIC);
        // A "Remastered 2011" edition (year 2011) of a song whose original release was 1975.
        ProviderTrack remaster = track("99", "Bohemian Rhapsody - Remastered 2011", "Queen", 2011);
        when(musicProvider.search(any(ProviderSearchQuery.class))).thenReturn(List.of(remaster));
        when(songRepository.findByProviderAndExternalId(any(), any())).thenReturn(Optional.empty());

        Song original = new Song(
                MusicProviderType.APPLE_MUSIC, "1", "Bohemian Rhapsody", "Queen", "A Night at the Opera",
                LocalDate.of(1975, 10, 31), 1975, MusicGenre.ROCK, "Rock", "US", null, "https://preview", null);
        when(songRepository.findByArtistIgnoreCase("Queen")).thenReturn(List.of(original));

        IngestionResult result = service.ingestQuery("bohemian rhapsody", "US");

        assertThat(result.deduplicated()).isEqualTo(1);
        assertThat(result.added()).isZero();
        assertThat(original.getCanonicalReleaseYear()).isNull(); // 1975 was already earlier than 2011, no change needed
        verify(songRepository, never()).save(any());
    }

    @Test
    void earlierDuplicateCorrectsTheCanonicalYearOfALaterOriginalRow() {
        when(musicProvider.getType()).thenReturn(MusicProviderType.APPLE_MUSIC);
        ProviderTrack earlierPressing = track("100", "Bohemian Rhapsody", "Queen", 1974);
        when(musicProvider.search(any(ProviderSearchQuery.class))).thenReturn(List.of(earlierPressing));
        when(songRepository.findByProviderAndExternalId(any(), any())).thenReturn(Optional.empty());

        Song laterRow = new Song(
                MusicProviderType.APPLE_MUSIC, "1", "Bohemian Rhapsody", "Queen", "A Night at the Opera",
                LocalDate.of(1975, 10, 31), 1975, MusicGenre.ROCK, "Rock", "US", null, "https://preview", null);
        when(songRepository.findByArtistIgnoreCase("Queen")).thenReturn(List.of(laterRow));

        service.ingestQuery("bohemian rhapsody", "US");

        assertThat(laterRow.getCanonicalReleaseYear()).isEqualTo(1974);
    }

    private ProviderTrack track(String externalId, String title, String artist, int year) {
        return new ProviderTrack(externalId, title, artist, "Album", LocalDate.of(year, 1, 1), "Rock", "US", null, "https://preview/" + externalId, 200000);
    }
}
