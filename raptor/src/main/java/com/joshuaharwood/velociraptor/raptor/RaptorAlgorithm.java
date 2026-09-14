package com.joshuaharwood.velociraptor.raptor;

import module java.base;

import com.joshuaharwood.velociraptor.raptor.model.QueueFactory;
import com.joshuaharwood.velociraptor.raptor.model.ScanResults;
import com.joshuaharwood.velociraptor.raptor.model.ScanResults.ImmutableScanResult;
import com.joshuaharwood.velociraptor.raptor.model.ScanResultsFactory;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.model.Transfer;
import com.joshuaharwood.velociraptor.raptor.model.Trip;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.jspecify.annotations.Nullable;

/**
 * Core RAPTOR algorithm implementation.
 * <p>
 * Implements the Round-Based Public Transit Optimized Router from Delling/Pajor/Werneck (2012).
 * The algorithm works in rounds, where round k computes the fastest way to reach each stop
 * with at most k trips. Each round consists of two phases: scanning routes and relaxing transfers.
 * <p>
 * <b>Reference:</b> RAPTOR (2012) Section 3, Algorithm 1
 *
 * <h2>Int-Indexed Hot Path</h2>
 * <p>
 * The hot path (route scanning + transfer relaxation) operates end-to-end on primitive {@code int}
 * stop and route indices - flat array reads and writes, no hash lookups and no boxing. {@link Stop}
 * objects and route id strings are translated to indices once at the public API boundary
 * ({@code stopToIndex}; origins, not-vias and the range destination), and back to objects only when
 * an improvement is recorded into the Stop-keyed connection index inside {@link ScanResults}.
 * <ul>
 *   <li>Arrival labels: {@code int[stopCount]} per round inside {@link ScanResults}.</li>
 *   <li>Marked stops: an {@code IntArrayList} of stop indices, deduplicated by a flag array.</li>
 *   <li>Route queue Q: {@code queuePositions[routeIdx]} holds the scan start position directly
 *       (the earliest marked stop's position on the route), filled by {@link QueueFactory}.</li>
 *   <li>Trip lookup: {@link RouteScanner} over trip arrays indexed by route.</li>
 * </ul>
 *
 * <h3>Memory Trade-offs</h3>
 * <p>
 * {@code QueueFactory.routeStopPosition} remains sparse (map-per-route) because each route
 * typically serves ~20 stops out of ~2000 total. A dense {@code int[routeCount][stopCount]} would
 * consume 4.6M ints = 18 MB per day, causing OOM during parallel precomputation.
 * {@code routePathsArray}, {@code transfersArray} and {@code interchangeArray} are dense because
 * they're indexed by a single dimension (routeIdx or stopIdx) and have manageable sizes.
 *
 * @param stopToIndex          maps Stop → int (0..N-1), used at the API boundary to enter the hot path
 * @param routePathsArray      {@code [routeIdx][position] → stopIdx}, ordered stop sequence per route
 * @param transfersArray       {@code [stopIdx] → Transfer[]}, available transfers from each stop
 * @param interchangeArray     {@code [stopIdx] → seconds}, minimum interchange time per stop
 * @param departureTimesAtStop unique departure times at each stop for rRAPTOR (Section 4.2)
 * @param scanResultsFactory   creates scan result storage (multilabel τ_k(p) in the paper)
 * @param queueFactory         builds marked stop queue Q
 * @param routeScannerFactory  creates route scanner for earliest trip lookup
 */
public record RaptorAlgorithm(Object2IntMap<Stop> stopToIndex,
                              int[][] routePathsArray,
                              Transfer[][] transfersArray,
                              int[] interchangeArray,
                              Map<Stop, int[]> departureTimesAtStop,
                              ScanResultsFactory scanResultsFactory,
                              QueueFactory queueFactory,
                              RouteScannerFactory routeScannerFactory) {

  private static final int ABSENT_VAL = -1;

  /**
   * Minimum interchange time (seconds) required when changing service at a stop. Stops not in the
   * index (not on any route) return 0.
   *
   * @param stop the stop to look up
   * @return the minimum interchange time in seconds, or 0 if the stop is unknown
   */
  public int interchangeTime(Stop stop) {
    final int stopIdx = stopToIndex.getInt(stop);
    return stopIdx == ABSENT_VAL ? 0 : interchangeArray[stopIdx];
  }

  private static final Transfer[] NO_TRANSFERS = new Transfer[0];

  /** The fixed links leaving a stop; empty for a stop with none or one the index does not know. */
  public Transfer[] transfersFrom(Stop stop) {
    final int stopIdx = stopToIndex.getInt(stop);
    if (stopIdx == ABSENT_VAL || transfersArray[stopIdx] == null) {
      return NO_TRANSFERS;
    }
    return transfersArray[stopIdx];
  }

  /**
   * Main RAPTOR scan algorithm.
   * <p>
   * Iterates in rounds until no more improvements are found. Each round k computes arrival times
   * τ_k(p) for all stops p reachable with at most k trips.
   * <p>
   * <b>Reference:</b> RAPTOR (2012) Section 3, Algorithm 1 (lines 6-29)
   *
   * @param origins map of origin stops to initial departure times (τ_0(p_s) = τ in the paper)
   * @return immutable scan results containing arrival times and parent pointers for all rounds
   */
  public ImmutableScanResult scan(Map<Stop, Integer> origins) {
    return scan(scanResultsFactory.create(origins), origins, Set.of());
  }

  public ImmutableScanResult scan(Map<Stop, Integer> origins, Set<Stop> notVias) {
    return scan(scanResultsFactory.create(origins), origins, notVias);
  }

  /**
   * As {@link #scan(Map, Set)}, applying {@code rules} to fixed links. The destination is needed
   * only for {@link FixedLinkRules#forbidTrailing()}; labels at every other stop are unaffected.
   *
   * @param destination the queried destination, or {@code null} when no trailing rule applies
   */
  public ImmutableScanResult scan(Map<Stop, Integer> origins, Set<Stop> notVias, @Nullable Stop destination, FixedLinkRules rules) {
    return scan(scanResultsFactory.create(origins), origins, notVias, destination, rules);
  }

  /**
   * Runs the RAPTOR scan over a caller-supplied {@link ScanResults}, allowing the result storage to
   * be allocated by the {@link ScanResultsFactory} (which knows the full stop set) before scanning.
   * <p>
   * The supplied {@code results} must already be seeded for {@code origins} by the factory: the
   * round-0 arrivals are read from it, and this method never seeds round 0 itself.
   * <p>
   * Mirrors the TS reference's {@code scan}: the minimum interchange is folded into the
   * arrival on alighting, and {@code notVias} stops are skipped inline (route scan breaks, transfer
   * scan continues past them).
   *
   * @param results pre-seeded scan-result storage (round 0 already populated for {@code origins})
   * @param origins origin stops mapped to their initial departure times, used as the first marked set
   * @param notVias stops the journey must not pass through (skipped inline during scanning)
   * @return immutable scan results for this departure
   */
  public ImmutableScanResult scan(ScanResults results, Map<Stop, Integer> origins, Set<Stop> notVias) {
    return scan(results, origins, notVias, null, FixedLinkRules.NONE);
  }

  public ImmutableScanResult scan(ScanResults results, Map<Stop, Integer> origins, Set<Stop> notVias,
                                  @Nullable Stop destination, FixedLinkRules rules) {
    final RouteScanner routeScanner = routeScannerFactory.create();
    final int[] queuePositions = newQueuePositions();
    final IntArrayList queuedRoutes = new IntArrayList();
    final boolean[] notVia = toNotViaFlags(notVias);
    final int destinationIdx = destination == null ? ABSENT_VAL : stopToIndex.getInt(destination);

    IntArrayList markedStops = toStopIndices(origins.keySet());

    while (!markedStops.isEmpty()) {
      results.addRound();

      // Phase 1: Scan routes (Algorithm 1, lines 15-23)
      scanRoutes(results, routeScanner, markedStops, queuePositions, queuedRoutes, notVia);

      // Phase 2: Relax footpaths (Algorithm 1, lines 24-26)
      scanTransfers(results, markedStops, notVia, rules, destinationIdx);

      markedStops = results.getMarkedStops();
    }

    return results.finalise();
  }

  /**
   * Scans all routes that serve marked stops from the previous round.
   * <p>
   * For each route r, finds the earliest marked stop, boards the earliest catchable trip t,
   * and traverses the route updating arrival times. May switch to an earlier trip if a
   * better arrival time is found at a subsequent stop along the route.
   * <p>
   * <b>Reference:</b> RAPTOR (2012) Section 3, Algorithm 1 (lines 15-23)
   * <br>
   * Key optimization: Section 3.1 describes marking stops improved in round k-1 to avoid
   * scanning routes that cannot be boarded in round k.
   *
   * @param results        scan results storage (τ_k(p) labels)
   * @param routeScanner   provides earliest trip lookup (et(r, p_i) in the paper)
   * @param markedStops    stop indices improved in the previous round
   * @param queuePositions scratch space: {@code [routeIdx] -> scan start position} once filled
   * @param queuedRoutes   scratch space: the queued route indices
   */
  private void scanRoutes(ScanResults results,
    RouteScanner routeScanner,
    IntArrayList markedStops,
    int[] queuePositions,
    IntArrayList queuedRoutes,
    boolean[] notVia) {
    queueFactory.fillQueue(queuePositions, queuedRoutes, markedStops);

    for (int q = 0; q < queuedRoutes.size(); q++) {
      final int routeIdx = queuedRoutes.getInt(q);
      // The queue stores the earliest marked stop's position on the route - the scan start.
      final int startPosition = queuePositions[routeIdx];

      // The trip-scan position is only sound within one traversal (see RouteScanner).
      routeScanner.startRoute(routeIdx);

      var boardingPoint = ABSENT_VAL;
      Trip trip = null;
      int tripIdx = Integer.MAX_VALUE; // index of trip on the route; MAX_VALUE while none is held
      final int[] routePath = this.routePathsArray[routeIdx];
      final int routePathLength = routePath.length;

      for (int pi = startPosition; pi < routePathLength; ++pi) {
        final int stopPi = routePath[pi];

        // Not-via: the held trip may not carry on through a forbidden stop, but the route may still
        // be boarded at a marked stop beyond it - the queue only records the earliest marked
        // position, so breaking here would lose every boarding downstream (BUGS.md 6.1).
        if (notVia[stopPi]) {
          trip = null;
          tripIdx = Integer.MAX_VALUE;
          boardingPoint = ABSENT_VAL;
          continue;
        }

        // The minimum interchange is folded into the arrival on alighting.
        final int interchange = this.interchangeArray[stopPi];
        final int previousArrival = results.previousArrival(stopPi);

        if (trip != null && trip.canAlight(pi)
            && trip.arrivalTime(pi) + interchange < results.getBestArrival(stopPi)) {
          results.setTrip(trip, stopPi, boardingPoint, pi, trip.arrivalTime(pi) + interchange);
        }

        // Board, or catch an earlier trip at this stop. Paper: "if tau_{k-1}(p_i) <= tau_dep(t, p_i)
        // then t <- et(r, p_i)". A trip catchable from the previous round's label departs at or after
        // it, and trips below the held one depart no later than it does, so when the held trip
        // departs before the label nothing below it can be caught and the lookup is skipped.
        // Otherwise the earliest catchable trip is at or below the held one (RouteScanner sweeps down
        // from it) and replaces it only when strictly earlier - never a later trip, and never
        // nothing. This is a separate step from alighting: a label inside the held trip's dwell can
        // both be improved by it and catch a trip that arrived before it (BUGS.md 1.4, 1.5).
        if (previousArrival != Integer.MAX_VALUE
            && routeScanner.canBoard(routeIdx, pi)
            && (trip == null || previousArrival <= trip.departureTime(pi))) {
          final int found = routeScanner.earliestTrip(routeIdx, pi, previousArrival);
          if (found != RouteScanner.NO_TRIP && found < tripIdx) {
            trip = routeScanner.trip(routeIdx, found);
            tripIdx = found;
            boardingPoint = pi;
          }
        }
      }
    }
  }

  /**
   * Relaxes footpaths (transfers) from all marked stops.
   * <p>
   * For each marked stop p, considers all footpaths (p, p') and updates arrival time
   * at p' if τ_k(p) + ℓ(p, p') improves upon the current best arrival.
   * <p>
   * <b>Reference:</b> RAPTOR (2012) Section 3, Algorithm 1 (lines 24-26)
   * <br>
   * Note: This implementation extends the paper with time-gated transfers (startTime/endTime)
   * to model transfers that are only available during certain times of day.
   *
   * @param results        scan results storage
   * @param markedStops    stop indices improved during route scanning phase
   * @param rules          where a fixed link may be taken (see {@link FixedLinkRules})
   * @param destinationIdx the queried destination's index, or {@code ABSENT_VAL}; only read for
   *                       {@link FixedLinkRules#forbidTrailing()}
   */
  private void scanTransfers(ScanResults results, IntArrayList markedStops, boolean[] notVia,
                             FixedLinkRules rules, int destinationIdx) {
    // Round 1 relaxes from the origins' seed labels: a fixed link here would begin the journey.
    if (rules.forbidLeading() && results.round() == 1) {
      return;
    }

    for (int m = 0; m < markedStops.size(); m++) {
      final int stopIdx = markedStops.getInt(m);
      final Transfer[] stopTransfers = this.transfersArray[stopIdx];

      if (stopTransfers == null) {
        continue;
      }

      // A fixed link from a stop that a fixed link reached would make two in a row.
      if (rules.forbidContiguous() && results.reachedByTransfer(stopIdx)) {
        continue;
      }

      for (Transfer transfer : stopTransfers) {
        final int stopPi = stopToIndex.getInt(transfer.destination());

        // Skip transfers to stops not in the index (not on any route)
        if (stopPi == ABSENT_VAL) {
          continue;
        }

        // Not-via: skip a transfer into a forbidden stop.
        if (notVia[stopPi]) {
          continue;
        }

        // A fixed link into the destination would end the journey.
        if (rules.forbidTrailing() && stopPi == destinationIdx) {
          continue;
        }

        // The destination's minimum interchange is added to the transfer arrival.
        final var arrival = results.previousArrival(stopIdx) + transfer.duration() + this.interchangeArray[stopPi];

        // Extension: time-gated transfers (not in paper)
        if (transfer.startTime() <= arrival && transfer.endTime() >= arrival
            && arrival < results.getBestArrival(stopPi)) {
          results.setTransfer(transfer, stopPi, arrival, this.interchangeArray[stopIdx], this.interchangeArray[stopPi]);
        }
      }
    }
  }

  /**
   * Creates the per-query scratch for {@link #scanRange}, converting {@code notVias} once. Reused
   * across a range query's departures: {@code scanRange} resets it instead of reallocating.
   */
  public RangeScanScratch createRangeScratch(Set<Stop> notVias) {
    return createRangeScratch(notVias, FixedLinkRules.NONE);
  }

  public RangeScanScratch createRangeScratch(Set<Stop> notVias, FixedLinkRules rules) {
    return new RangeScanScratch(newQueuePositions(),
                                new IntArrayList(routePathsArray.length),
                                routeScannerFactory.create(),
                                toNotViaFlags(notVias),
                                rules);
  }

  /**
   * Range-scans a single departure as one iteration of rRAPTOR, mirroring the TS reference's
   * {@code RaptorAlgorithm.scanRange} + {@code RaptorTimeRangeQuery}. The supplied {@code results}
   * <b>retains its per-round {@code kArrivals} labels</b> from earlier (later-departing) calls and
   * prunes with a per-round destination-arrival domination bound. Same alight-interchange model and
   * inline not-via as {@link #scan}.
   *
   * @param results     reused scan-result storage carrying retained kArrivals across departures
   * @param scratch     reused per-query scratch ({@link #createRangeScratch}); reset in here
   * @param origins     origin stops mapped to this departure's time
   * @param destination the queried destination, for the domination bound
   * @return immutable scan results for this departure (its own self-consistent connection index)
   */
  public ImmutableScanResult scanRange(ScanResults results, RangeScanScratch scratch, Map<Stop, Integer> origins, Stop destination) {
    final int destinationIdx = stopToIndex.getInt(destination);
    results.beginRangeDeparture(destinationIdx);

    final IntArrayList originIndices = new IntArrayList(origins.size());
    for (Map.Entry<Stop, Integer> origin : origins.entrySet()) {
      final int stopIdx = stopToIndex.getInt(origin.getKey());
      results.seedRangeOrigin(stopIdx, origin.getValue());
      originIndices.add(stopIdx);
    }

    IntArrayList markedStops = originIndices;

    while (!markedStops.isEmpty()) {
      results.addRangeRound();
      scanRoutesRange(results, scratch.routeScanner, markedStops, scratch.queuePositions, scratch.queuedRoutes, scratch.notVia);
      scanTransfersRange(results, markedStops, scratch.notVia, scratch.rules, destinationIdx);
      markedStops = results.getMarkedStops();
    }

    return results.finalise();
  }

  /**
   * Range-mode route scan: like {@link #scanRoutes} but the alight-improves test uses the per-round
   * destination-domination bound ({@link ScanResults#rangeBound}). Mirrors the TS reference's {@code scanRange}.
   */
  private void scanRoutesRange(ScanResults results,
    RouteScanner routeScanner,
    IntArrayList markedStops,
    int[] queuePositions,
    IntArrayList queuedRoutes,
    boolean[] notVia) {
    queueFactory.fillQueue(queuePositions, queuedRoutes, markedStops);

    for (int q = 0; q < queuedRoutes.size(); q++) {
      final int routeIdx = queuedRoutes.getInt(q);
      final int startPosition = queuePositions[routeIdx];

      // The trip-scan position is only sound within one traversal (see RouteScanner).
      routeScanner.startRoute(routeIdx);

      var boardingPoint = ABSENT_VAL;
      Trip trip = null;
      int tripIdx = Integer.MAX_VALUE; // index of trip on the route; MAX_VALUE while none is held
      final int[] routePath = this.routePathsArray[routeIdx];
      final int routePathLength = routePath.length;

      for (int pi = startPosition; pi < routePathLength; ++pi) {
        final int stopPi = routePath[pi];

        // Not-via: drop the held trip and keep scanning for a boarding beyond it (see scanRoutes).
        if (notVia[stopPi]) {
          trip = null;
          tripIdx = Integer.MAX_VALUE;
          boardingPoint = ABSENT_VAL;
          continue;
        }

        final int interchange = this.interchangeArray[stopPi];
        final int previousArrival = results.previousArrival(stopPi);

        if (trip != null && trip.canAlight(pi)
            && trip.arrivalTime(pi) + interchange < results.rangeBound(stopPi)) {
          results.setTrip(trip, stopPi, boardingPoint, pi, trip.arrivalTime(pi) + interchange);
        }

        // Same re-board rule as scanRoutes.
        if (previousArrival != Integer.MAX_VALUE
            && routeScanner.canBoard(routeIdx, pi)
            && (trip == null || previousArrival <= trip.departureTime(pi))) {
          final int found = routeScanner.earliestTrip(routeIdx, pi, previousArrival);
          if (found != RouteScanner.NO_TRIP && found < tripIdx) {
            trip = routeScanner.trip(routeIdx, found);
            tripIdx = found;
            boardingPoint = pi;
          }
        }
      }
    }
  }

  /**
   * Range-mode transfer relaxation: like {@link #scanTransfers} but bounded, as the range route scan
   * is, by {@link ScanResults#rangeBound} - the labels retained from later departures plus the
   * destination's - rather than by the per-departure {@code bestArrivals}. Those are reset every
   * departure, so once a later departure has labelled a stop and this departure's route scan has
   * (rightly) declined to re-record the same arrival there, {@code bestArrivals} still reads as
   * unreached and a fixed link into the stop would record whatever it arrived at, overwriting the
   * retained label with a later one and returning a journey a later departure dominates (BUGS.md 6.2).
   * {@code currentRound[p] <= bestArrivals[p]} always holds within a departure, so the range bound
   * is never looser than the per-departure one.
   */
  private void scanTransfersRange(ScanResults results, IntArrayList markedStops, boolean[] notVia,
                                  FixedLinkRules rules, int destinationIdx) {
    // Same three rules as scanTransfers.
    if (rules.forbidLeading() && results.round() == 1) {
      return;
    }

    for (int m = 0; m < markedStops.size(); m++) {
      final int stopIdx = markedStops.getInt(m);
      final Transfer[] stopTransfers = this.transfersArray[stopIdx];

      if (stopTransfers == null) {
        continue;
      }

      if (rules.forbidContiguous() && results.reachedByTransfer(stopIdx)) {
        continue;
      }

      for (Transfer transfer : stopTransfers) {
        final int stopPi = stopToIndex.getInt(transfer.destination());

        if (stopPi == ABSENT_VAL) {
          continue;
        }

        if (notVia[stopPi]) {
          continue;
        }

        if (rules.forbidTrailing() && stopPi == destinationIdx) {
          continue;
        }

        final var arrival = results.previousArrival(stopIdx) + transfer.duration() + this.interchangeArray[stopPi];

        if (transfer.startTime() <= arrival && transfer.endTime() >= arrival
            && arrival < results.rangeBound(stopPi)) {
          results.setTransfer(transfer, stopPi, arrival, this.interchangeArray[stopIdx], this.interchangeArray[stopPi]);
        }
      }
    }
  }

  private int[] newQueuePositions() {
    final int[] queuePositions = new int[routePathsArray.length];
    Arrays.fill(queuePositions, ABSENT_VAL);
    return queuePositions;
  }

  /**
   * Indices of the stops the timetable knows. A stop that is on no route and at the end of no
   * footpath that day is unreachable and is dropped, so an unknown origin scans nothing and yields
   * no journeys rather than indexing an array at -1 (BUGS.md 1.2).
   */
  private IntArrayList toStopIndices(Set<Stop> stops) {
    final IntArrayList indices = new IntArrayList(stops.size());
    for (Stop stop : stops) {
      final int stopIdx = stopToIndex.getInt(stop);
      if (stopIdx != ABSENT_VAL) {
        indices.add(stopIdx);
      }
    }
    return indices;
  }

  private boolean[] toNotViaFlags(Set<Stop> notVias) {
    final boolean[] notVia = new boolean[interchangeArray.length];
    for (Stop stop : notVias) {
      final int stopIdx = stopToIndex.getInt(stop);
      if (stopIdx != ABSENT_VAL) {
        notVia[stopIdx] = true;
      }
    }
    return notVia;
  }
}
