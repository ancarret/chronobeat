package com.chronobeat.util;

import java.util.List;

/**
 * Thin abstraction over randomness so {@code SongSelectionService} can be
 * unit-tested deterministically without mocking {@link java.util.Random} internals.
 */
public interface RandomProvider {

    int nextInt(int bound);

    <T> T pick(List<T> items);
}
