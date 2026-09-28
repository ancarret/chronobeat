package com.chronobeat.service;

import com.chronobeat.domain.MusicGenre;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Maps a provider's free-text genre label onto our fixed {@link MusicGenre} taxonomy.
 * Apple's {@code primaryGenreName} vocabulary is inconsistent (e.g. "Hip-Hop/Rap",
 * "Alternative", "Singer/Songwriter"), so matching is done on normalized substrings
 * rather than exact equality. Anything unrecognized falls back to {@link MusicGenre#OTHER}
 * rather than guessing, per the "do not fabricate classifications" rule in the spec.
 */
@Component
public class GenreNormalizer {

    private static final Map<String, MusicGenre> KEYWORD_TO_GENRE = Map.ofEntries(
            Map.entry("hip-hop", MusicGenre.HIP_HOP_RAP),
            Map.entry("hip hop", MusicGenre.HIP_HOP_RAP),
            Map.entry("rap", MusicGenre.HIP_HOP_RAP),
            Map.entry("electronic", MusicGenre.ELECTRONIC),
            Map.entry("dance", MusicGenre.ELECTRONIC),
            Map.entry("house", MusicGenre.ELECTRONIC),
            Map.entry("techno", MusicGenre.ELECTRONIC),
            Map.entry("metal", MusicGenre.METAL),
            Map.entry("r&b", MusicGenre.RNB_SOUL),
            Map.entry("soul", MusicGenre.RNB_SOUL),
            Map.entry("reggaeton", MusicGenre.REGGAETON_LATIN_URBAN),
            Map.entry("urbano", MusicGenre.REGGAETON_LATIN_URBAN),
            Map.entry("latino", MusicGenre.LATIN),
            Map.entry("latin", MusicGenre.LATIN),
            Map.entry("alternative", MusicGenre.ALTERNATIVE_INDIE),
            Map.entry("indie", MusicGenre.ALTERNATIVE_INDIE),
            Map.entry("rock", MusicGenre.ROCK),
            Map.entry("pop", MusicGenre.POP));

    public MusicGenre normalize(String rawGenre) {
        if (rawGenre == null || rawGenre.isBlank()) {
            return MusicGenre.OTHER;
        }
        String normalized = rawGenre.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, MusicGenre> entry : KEYWORD_TO_GENRE.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return MusicGenre.OTHER;
    }
}
