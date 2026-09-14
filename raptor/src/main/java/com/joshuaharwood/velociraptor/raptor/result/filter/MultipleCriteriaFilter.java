package com.joshuaharwood.velociraptor.raptor.result.filter;

import com.joshuaharwood.velociraptor.raptor.model.JourneyFilter;
import com.joshuaharwood.velociraptor.raptor.result.Journey;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Filters journeys based on a number of configurable criteria
 */
public class MultipleCriteriaFilter implements JourneyFilter<Journey> {

  /**
   * Returns true if b arrives before or at the same time as a
   */
  public static final BiPredicate<Journey, Journey> earliestArrival =
    (a, b) -> b.arrivalTime() <= a.arrivalTime();

  /**
   * Returns true if b has the same or fewer changes than a
   */
  public static final BiPredicate<Journey, Journey> leastChanges =
    (a, b) -> b.legs().size() <= a.legs().size();

  private final List<BiPredicate<Journey, Journey>> criteria;

  public MultipleCriteriaFilter(List<BiPredicate<Journey, Journey>> criteria) {
    this.criteria = criteria;
  }

  public MultipleCriteriaFilter() {
    this(List.of(earliestArrival, leastChanges));
  }

  /**
   * Sort the journeys and then apply the criteria
   */
  @Override
  public List<Journey> apply(List<Journey> journeys) {
    var sorted = new ArrayList<>(journeys);
    sorted.sort(this::sort);

    var results = new ArrayList<Journey>();

    for (int i = 0; i < sorted.size(); i++) {
      if (compare(sorted.get(i), i, sorted)) {
        results.add(sorted.get(i));
      }
    }

    return results;
  }

  /**
   * Sort by departure time ascending and arrival time descending as a tie breaker
   */
  private int sort(Journey a, Journey b) {
    return a.departureTime() != b.departureTime()
      ? a.departureTime() - b.departureTime()
      : b.arrivalTime() - a.arrivalTime();
  }

  /**
   * Keeps the journey as long as there is no subsequent journey that is better in every regard.
   */
  private boolean compare(Journey journeyA, int index, List<Journey> journeys) {
    for (int j = index + 1; j < journeys.size(); j++) {
      Journey journeyB = journeys.get(j);

      if (criteria.stream().allMatch(c -> c.test(journeyA, journeyB))) {
        return false;
      }
    }

    return true;
  }
}
