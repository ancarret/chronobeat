package com.chronobeat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chronobeat.service.TimelineValidationService.ValidationResult;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Exhaustive tests for the core chronology rule: an insertion index is correct
 * iff its left neighbor's year &le; mystery year &le; its right neighbor's year.
 */
class TimelineValidationServiceTest {

    private final TimelineValidationService service = new TimelineValidationService();

    @Nested
    class EmptyTimeline {
        @Test
        void anyMysteryYearIsValidOnlyAtIndexZero() {
            ValidationResult result = service.validate(List.of(), 0, 1995);
            assertThat(result.correct()).isTrue();
            assertThat(result.validIndices()).containsExactly(0);
        }
    }

    @Nested
    class SingleEntryTimeline {
        // timeline: [1991]
        @Test
        void beforeIsCorrectWhenEarlier() {
            ValidationResult result = service.validate(List.of(1991), 0, 1980);
            assertThat(result.correct()).isTrue();
        }

        @Test
        void afterIsCorrectWhenLater() {
            ValidationResult result = service.validate(List.of(1991), 1, 2008);
            assertThat(result.correct()).isTrue();
        }

        @Test
        void afterIsWrongWhenEarlier() {
            ValidationResult result = service.validate(List.of(1991), 1, 1980);
            assertThat(result.correct()).isFalse();
        }

        @Test
        void beforeIsWrongWhenLater() {
            ValidationResult result = service.validate(List.of(1991), 0, 2008);
            assertThat(result.correct()).isFalse();
        }

        @Test
        void tieYearIsValidOnBothSides() {
            ValidationResult result = service.validate(List.of(1991), 0, 1991);
            assertThat(result.correct()).isTrue();
            assertThat(result.validIndices()).containsExactlyInAnyOrder(0, 1);

            ValidationResult result2 = service.validate(List.of(1991), 1, 1991);
            assertThat(result2.correct()).isTrue();
        }
    }

    @Nested
    class ThreeEntryTimeline {
        // timeline: [1977, 1991, 2008] (Fleetwood Mac, Nirvana, Coldplay - the README example)
        private final List<Integer> timeline = List.of(1977, 1991, 2008);

        @Test
        void beforeFirst() {
            assertThat(service.validate(timeline, 0, 1965).correct()).isTrue();
        }

        @Test
        void betweenFirstAndSecond_wonderwall1995GoesAfterNirvana() {
            // Oasis - Wonderwall - 1995 belongs between Nirvana (1991) and Coldplay (2008): index 2
            assertThat(service.validate(timeline, 2, 1995).correct()).isTrue();
        }

        @Test
        void wrongInsertionIsRejected() {
            // 1995 does NOT belong before Nirvana (index 1 would mean "between 1977 and 1991")
            assertThat(service.validate(timeline, 1, 1995).correct()).isFalse();
        }

        @Test
        void afterLast() {
            assertThat(service.validate(timeline, 3, 2015).correct()).isTrue();
            assertThat(service.validate(timeline, 2, 2015).correct()).isFalse();
        }

        @Test
        void allValidIndicesAreComputedForAGivenYear() {
            // A song from 1991 (tying the middle entry) is valid at index 1 (before it) or 2 (after it)
            Set<Integer> valid = service.computeValidIndices(timeline, 1991);
            assertThat(valid).containsExactlyInAnyOrder(1, 2);
        }
    }

    @Nested
    class DuplicateYearsInTimeline {
        // Two existing entries already share a year: [1990, 1990, 2000]
        private final List<Integer> timeline = List.of(1990, 1990, 2000);

        @Test
        void allThreeMiddlePositionsAreValidForATyingYear() {
            // A third 1990 song is chronologically indistinguishable from the other two,
            // so every index that keeps it adjacent to the 1990 block must be accepted.
            Set<Integer> valid = service.computeValidIndices(timeline, 1990);
            assertThat(valid).containsExactlyInAnyOrder(0, 1, 2);
        }

        @Test
        void placingBetweenTheDuplicatesIsCorrect() {
            assertThat(service.validate(timeline, 1, 1990).correct()).isTrue();
        }

        @Test
        void placingAfterTheLastEntryIsWrongForAnEarlierYear() {
            assertThat(service.validate(timeline, 3, 1985).correct()).isFalse();
        }
    }

    @ParameterizedTest(name = "timeline={0}, index={1}, mysteryYear={2} -> correct={3}")
    @CsvSource({
        "'1980,1990,2000', 0, 1970, true",
        "'1980,1990,2000', 1, 1985, true",
        "'1980,1990,2000', 2, 1995, true",
        "'1980,1990,2000', 3, 2010, true",
        "'1980,1990,2000', 1, 1970, false",
        "'1980,1990,2000', 2, 1975, false",
        "'1980,1990,2000', 0, 2010, false"
    })
    void tableDrivenBoundaryChecks(String csvTimeline, int index, int mysteryYear, boolean expectedCorrect) {
        List<Integer> timeline = List.of(csvTimeline.split(",")).stream().map(Integer::parseInt).toList();
        assertThat(service.validate(timeline, index, mysteryYear).correct()).isEqualTo(expectedCorrect);
    }
}
