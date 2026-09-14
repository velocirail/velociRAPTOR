package com.joshuaharwood.velociraptor.raptor.query;

import com.joshuaharwood.velociraptor.raptor.FixedLinkRules;
import com.joshuaharwood.velociraptor.raptor.RaptorAlgorithm;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.result.ResultsFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Searches for journeys departing after a specific time, from a single pass of the RAPTOR algorithm.
 * <p>
 * Behavioural 1-to-1 port of the TS reference's {@code RaptorDepartAfterQuery}: a single
 * {@link RaptorAlgorithm#scan} from one origin, then result extraction for one destination. Generic
 * over the result type {@code T} (the TS reference's {@code RaptorDepartAfterQuery<T>}).
 */
public class DepartAfterQuery<T> {
  private final RaptorAlgorithm raptor;
  private final ResultsFactory<T> resultsFactory;
  private final FixedLinkRules fixedLinkRules;

  public DepartAfterQuery(RaptorAlgorithm raptor, ResultsFactory<T> resultsFactory) {
    this(raptor, resultsFactory, FixedLinkRules.NONE);
  }

  /** @param fixedLinkRules where a journey may use a fixed link; enforced inside the scan */
  public DepartAfterQuery(RaptorAlgorithm raptor, ResultsFactory<T> resultsFactory, FixedLinkRules fixedLinkRules) {
    this.raptor = raptor;
    this.resultsFactory = resultsFactory;
    this.fixedLinkRules = fixedLinkRules;
  }

  public List<T> plan(Stop origin, Stop destination, LocalDate date, int time) {
    return plan(origin, destination, date, time, Set.of());
  }

  public List<T> plan(Stop origin, Stop destination, LocalDate date, int time, Set<Stop> notVias) {
    final var scan = raptor.scan(Map.of(origin, time), notVias, destination, fixedLinkRules);
    // the TS reference's RaptorDepartAfterQuery passes no currentSearchTime to getResults, so no upper-bound
    // departure filter is applied (every journey from this single scan departs at/after `time`).
    return resultsFactory.getResults(scan.kConnections(), destination);
  }
}
