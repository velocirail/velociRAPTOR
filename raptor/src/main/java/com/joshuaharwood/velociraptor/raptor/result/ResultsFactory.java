package com.joshuaharwood.velociraptor.raptor.result;

import com.joshuaharwood.velociraptor.raptor.model.ResultConnectionIndex;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

@FunctionalInterface
public interface ResultsFactory<R> extends BiFunction<Map<Stop, Map<Integer, ResultConnectionIndex>>, Stop, List<R>> {

  default List<R> getResults(Map<Stop, Map<Integer, ResultConnectionIndex>> kConnections, Stop destination) {
    return apply(kConnections, destination);
  }

  /**
   * @param currentSearchTime lower bound on the first leg's departure, as seconds since the service
   *                          date's midnight (may exceed 86400 for GTFS &gt;24h services); {@code
   *                          null} for no lower bound. Carried in seconds - not {@code LocalTime} -
   *                          so after-midnight departures are not wrapped back into the same day.
   */
  default List<R> getResults(Map<Stop, Map<Integer, ResultConnectionIndex>> kConnections, Stop destination, @Nullable Integer currentSearchTime) {
    return apply(kConnections, destination);
  }
}
