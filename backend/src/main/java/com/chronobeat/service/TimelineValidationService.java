package com.chronobeat.service;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

/**
 * Pure domain logic for deciding whether an insertion index is chronologically
 * correct. Deliberately decoupled from JPA entities (works on plain year
 * integers) so it stays trivial to unit test exhaustively &mdash; this is the
 * most important piece of game logic in the codebase.
 *
 * <p>An index {@code i} (0 = before the first entry, {@code size} = after the
 * last) is valid when the entry immediately before it has a year &le; the
 * mystery year AND the entry immediately after it has a year &ge; the mystery
 * year. When the mystery year ties an existing entry, both the index right
 * before and right after that entry are valid, so a tie is never unfairly
 * marked wrong.
 */
@Service
public class TimelineValidationService {

    public record ValidationResult(boolean correct, Set<Integer> validIndices) {}

    public ValidationResult validate(List<Integer> sortedTimelineYears, int submittedIndex, int mysteryYear) {
        Set<Integer> validIndices = computeValidIndices(sortedTimelineYears, mysteryYear);
        return new ValidationResult(validIndices.contains(submittedIndex), validIndices);
    }

    public Set<Integer> computeValidIndices(List<Integer> sortedTimelineYears, int mysteryYear) {
        Set<Integer> validIndices = new TreeSet<>();
        int size = sortedTimelineYears.size();
        for (int i = 0; i <= size; i++) {
            boolean lowerBoundOk = i == 0 || sortedTimelineYears.get(i - 1) <= mysteryYear;
            boolean upperBoundOk = i == size || mysteryYear <= sortedTimelineYears.get(i);
            if (lowerBoundOk && upperBoundOk) {
                validIndices.add(i);
            }
        }
        return validIndices;
    }
}
