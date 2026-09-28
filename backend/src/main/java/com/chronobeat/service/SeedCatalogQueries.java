package com.chronobeat.service;

import java.util.List;

/**
 * Curated artist search terms used to bootstrap a playable catalog on first run
 * (see README "Development data"). Each term is searched against the configured
 * {@code MusicProvider} and the results are normalized/deduplicated by
 * {@link CatalogIngestionService}. The list intentionally spans many decades and
 * genres so a fresh database can support every date-range/genre filter combo
 * without manual data entry.
 */
public final class SeedCatalogQueries {

    private SeedCatalogQueries() {}

    public static final List<String> DEFAULT_QUERIES = List.of(
            // 1950s-1960s
            "Elvis Presley", "Chuck Berry", "The Beatles", "The Beach Boys", "Bob Dylan",
            "The Rolling Stones", "Aretha Franklin", "Otis Redding", "The Supremes", "Sam Cooke",
            "Johnny Cash", "Ray Charles", "The Kinks", "The Who", "Simon & Garfunkel",
            // 1970s
            "Fleetwood Mac", "Led Zeppelin", "Pink Floyd", "David Bowie", "Queen",
            "Stevie Wonder", "Elton John", "Marvin Gaye", "The Eagles", "Bee Gees",
            "ABBA", "Bob Marley", "Earth Wind & Fire", "Al Green", "Black Sabbath",
            // 1980s
            "Michael Jackson", "Madonna", "Prince", "U2", "Whitney Houston",
            "Duran Duran", "Tina Turner", "Guns N' Roses", "Depeche Mode", "Talking Heads",
            "Run-D.M.C.", "The Police", "Eurythmics", "Metallica", "Bon Jovi",
            // 1990s
            "Nirvana", "Oasis", "Radiohead", "Mariah Carey", "TLC",
            "Backstreet Boys", "Notorious B.I.G.", "Tupac Shakur", "Alanis Morissette", "Pearl Jam",
            "Red Hot Chili Peppers", "Destiny's Child", "Shania Twain", "Green Day", "Spice Girls",
            // 2000s
            "Beyonce", "Eminem", "Coldplay", "OutKast", "Rihanna",
            "Kanye West", "Amy Winehouse", "Green Day", "Linkin Park", "Christina Aguilera",
            "Usher", "Black Eyed Peas", "Daft Punk", "Gorillaz", "The Killers",
            // 2010s
            "Adele", "Taylor Swift", "Drake", "Ed Sheeran", "Bruno Mars",
            "Lady Gaga", "Kendrick Lamar", "The Weeknd", "Dua Lipa", "Billie Eilish",
            "Imagine Dragons", "Arctic Monkeys", "Daddy Yankee", "J Balvin", "Calvin Harris",
            // 2020s
            "Olivia Rodrigo", "Harry Styles", "Bad Bunny", "Doja Cat", "SZA",
            "Karol G", "Tyler, The Creator", "Dua Lipa", "The Weeknd", "Peso Pluma",
            // Genre-leaning terms for breadth
            "reggaeton clasicos", "salsa clasica", "musica disco", "synthpop", "grunge",
            "classic soul", "new wave 80s", "trap latino", "indie rock 2010s", "heavy metal classics");
}
