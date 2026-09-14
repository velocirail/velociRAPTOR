package com.joshuaharwood.velociraptor.raptor.model;

import com.joshuaharwood.velociraptor.raptor.model.Leg.TransferLeg;
import com.joshuaharwood.velociraptor.raptor.model.ResultConnectionIndex.ResultConnection;
import com.joshuaharwood.velociraptor.raptor.result.ConnectionIndex;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Mutable multilabel storage for a scan, addressed by <b>stop index</b> rather than {@link Stop}:
 * the per-round arrival labels τ_k(p) and the best-arrival bounds live in flat {@code int[]} arrays
 * indexed by the algorithm's stop indices, so the hot path reads and writes them without hashing.
 * Stop objects appear only at the result boundary - the Stop-keyed {@code kConnections} written on
 * improvement, and {@link ImmutableScanResult} - resolved via {@code indexToStop}.
 * <p>
 * Rounds carry per-round labels only (a fresh round starts at {@code Integer.MAX_VALUE}, mirroring
 * the TS reference's per-round maps); {@code bestArrivals} bounds alighting across rounds. Range
 * (rRAPTOR) mode instead copies labels forward and retains them across departures - see
 * {@link #addRangeRound}.
 */
public class ScanResults {

  private static final int ABSENT_VAL = -1;

  private final int[] bestArrivals;         // [stopIdx] -> best arrival this departure
  private final ArrayList<int[]> kArrivals; // [k][stopIdx] -> arrival with at most k trips
  private final ConnectionIndex kConnections;
  private final Stop[] indexToStop;
  private int k = 0;

  // The rounds the hot path touches, cached in fields so previousArrival/rangeBound don't re-fetch
  // from the list on every stop visit.
  private int[] currentRound;
  private int[] previousRound;

  // Stops improved in the current round - the next round's marked set (the TS reference: markedStops in
  // scan, improvedStops in scanRange). Double-buffered so the list handed out by getMarkedStops
  // survives the next addRound; markedFlags dedupes within the current round only. Sized to the
  // stop count up front - growing from the default showed up in profiles at 200 req/s.
  private IntArrayList markedThisRound;
  private IntArrayList markedLastRound;
  private final boolean[] markedFlags;

  // Whether a stop's label this round / last round was set by a fixed link rather than a trip, for
  // FixedLinkRules.forbidContiguous. Double-buffered with the marked lists. Only marked stops are
  // ever read, and every marked stop was written in its round, so stale entries are harmless.
  private boolean[] byTransferThisRound;
  private boolean[] byTransferLastRound;

  // Range (rRAPTOR) mode only: the queried destination's index for the per-round domination bound,
  // or ABSENT_VAL when the destination is not on any route (the bound is then unbounded, matching
  // the map-based getInt default). See the TS reference's scanRange.
  private int rangeDestination = ABSENT_VAL;

  ScanResults(int[] bestArrivals,
              ArrayList<int[]> kArrivals,
              ConnectionIndex kConnections,
              Stop[] indexToStop) {
    this.bestArrivals = bestArrivals;
    this.kArrivals = kArrivals;
    this.kConnections = kConnections;
    this.indexToStop = indexToStop;
    this.currentRound = kArrivals.getFirst();
    this.previousRound = this.currentRound; // never read before the first addRound/addRangeRound
    this.markedFlags = new boolean[bestArrivals.length];
    this.markedThisRound = new IntArrayList(bestArrivals.length);
    this.markedLastRound = new IntArrayList(bestArrivals.length);
    this.byTransferThisRound = new boolean[bestArrivals.length];
    this.byTransferLastRound = new boolean[bestArrivals.length];
  }

  public void addRound() {
    final int[] previous = kArrivals.get(k);
    k++;

    final int[] current;
    if (k < kArrivals.size()) {
      current = kArrivals.get(k);
    } else {
      current = new int[previous.length];
      kArrivals.add(current);
    }
    // A fresh (non-range) round holds only this round's labels, as with the per-round maps.
    Arrays.fill(current, Integer.MAX_VALUE);

    previousRound = previous;
    currentRound = current;
    swapMarked();
  }

  public int previousArrival(int stopIdx) {
    return previousRound[stopIdx];
  }

  /** The current round number k (1-based once scanning has started). */
  public int round() {
    return k;
  }

  public void setTrip(Trip trip, int stopPi, int startIndex, int endIndex, int arrivalAtEnd) {
    // Alight cost: the caller passes arrivalAtEnd already including this stop's minimum
    // interchange (folded in on alighting in RaptorAlgorithm.scanRoutes), so it is stored as-is.
    currentRound[stopPi] = arrivalAtEnd;
    bestArrivals[stopPi] = arrivalAtEnd;
    byTransferThisRound[stopPi] = false;
    stopConnections(indexToStop[stopPi]).put(k, new ResultConnection(trip, startIndex, endIndex));
    mark(stopPi);
  }

  public void setTransfer(Transfer transfer, int destinationIdx, int time, int originInterchange, int destinationInterchange) {
    currentRound[destinationIdx] = time;
    bestArrivals[destinationIdx] = time;
    byTransferThisRound[destinationIdx] = true;
    stopConnections(transfer.destination()).put(k, new TransferLeg(transfer.origin(),
                                                                   transfer.destination(),
                                                                   transfer.duration(),
                                                                   transfer.startTime(),
                                                                   transfer.endTime(),
                                                                   originInterchange,
                                                                   destinationInterchange,
                                                                   transfer.mode()));
    mark(destinationIdx);
  }

  /**
   * The per-round connections at a stop, created on first improvement. Explicit get/put rather
   * than computeIfAbsent (the lambda dispatch showed up in profiles), and sized for the handful
   * of rounds a stop can hold rather than the default 16 - these maps were the top allocation
   * site under load, re-created per improved stop per departure.
   */
  private Map<Integer, ResultConnectionIndex> stopConnections(Stop stop) {
    Map<Integer, ResultConnectionIndex> connections = kConnections.get(stop);
    if (connections == null) {
      connections = new Int2ObjectOpenHashMap<>(4);
      kConnections.put(stop, connections);
    }
    return connections;
  }

  public int getBestArrival(int stopIdx) {
    return bestArrivals[stopIdx];
  }

  /** The stops improved during the current round - the next round's marked set / queue seed. */
  public IntArrayList getMarkedStops() {
    return markedThisRound;
  }

  /**
   * Whether the previous round's label at a stop was set by a fixed link rather than a trip (or the
   * origin seed). Meaningful only for stops marked in the previous round.
   */
  public boolean reachedByTransfer(int stopIdx) {
    return byTransferLastRound[stopIdx];
  }

  // ---------------------------------------------------------------------------------------------
  // Range (rRAPTOR) mode: label retention across departures, mirroring the TS reference's
  // RaptorAlgorithm.scanRange + RaptorTimeRangeQuery. kArrivals is RETAINED across departures (the
  // cross-departure pruning bound); bestArrivals and kConnections reset per departure.
  // ---------------------------------------------------------------------------------------------

  /**
   * Begins a fresh departure of a range scan. Resets the round counter, the connection index and
   * {@code bestArrivals} (so {@link #getBestArrival} defaults to {@code MAX_VALUE}), but retains
   * {@code kArrivals} - the labels carried from later departures that prune this one. The caller
   * seeds round 0 via {@link #seedRangeOrigin}.
   *
   * @param destinationIdx the queried destination's stop index, or {@code -1} if not indexed
   */
  public void beginRangeDeparture(int destinationIdx) {
    this.rangeDestination = destinationIdx;
    this.k = 0;
    kConnections.clear();
    Arrays.fill(bestArrivals, Integer.MAX_VALUE);
    currentRound = kArrivals.getFirst();
    previousRound = currentRound;
    clearMarked(markedThisRound);
    clearMarked(markedLastRound);
  }

  /** Seeds an origin into round 0 and {@code bestArrivals} for the departure being range-scanned. */
  public void seedRangeOrigin(int stopIdx, int departureTime) {
    kArrivals.getFirst()[stopIdx] = departureTime;
    bestArrivals[stopIdx] = departureTime;
    // The seed is not a fixed link; this buffer becomes last round's when round 1 opens.
    byTransferThisRound[stopIdx] = false;
  }

  /**
   * Opens round {@code k} of a range scan, copying the previous round's arrivals forward into this
   * round's retained labels ({@code kArrivals[k][p] = min(kArrivals[k][p], kArrivals[k-1][p])},
   * as in the TS reference), and starting a fresh improved-stops set.
   */
  public void addRangeRound() {
    final int[] previous = kArrivals.get(k);
    k++;

    final int[] current;
    if (k < kArrivals.size()) {
      current = kArrivals.get(k);
      for (int i = 0; i < current.length; i++) {
        if (previous[i] < current[i]) {
          current[i] = previous[i];
        }
      }
    } else {
      // No retained labels for this round yet: the copy-forward of an all-MAX round is a clone.
      current = previous.clone();
      kArrivals.add(current);
    }

    previousRound = previous;
    currentRound = current;
    swapMarked();
  }

  /**
   * The range-mode route domination bound: {@code min(kArrivals[k][p], kArrivals[k][dest])}. A
   * boarding records only if it beats this, so a later departure's labels prune an earlier one.
   */
  public int rangeBound(int stopIdx) {
    final int bound = currentRound[stopIdx];
    return rangeDestination == ABSENT_VAL ? bound : Math.min(bound, currentRound[rangeDestination]);
  }

  public ImmutableScanResult finalise() {
    return new ImmutableScanResult(kConnections, bestArrivals, indexToStop);
  }

  private void mark(int stopIdx) {
    if (!markedFlags[stopIdx]) {
      markedFlags[stopIdx] = true;
      markedThisRound.add(stopIdx);
    }
  }

  /** Retires the finished round's marks: its list lives on as last round's, the spare is reused. */
  private void swapMarked() {
    for (int i = 0; i < markedThisRound.size(); i++) {
      markedFlags[markedThisRound.getInt(i)] = false;
    }
    final IntArrayList recycled = markedLastRound;
    markedLastRound = markedThisRound;
    markedThisRound = recycled;
    markedThisRound.clear();

    final boolean[] recycledFlags = byTransferLastRound;
    byTransferLastRound = byTransferThisRound;
    byTransferThisRound = recycledFlags;
  }

  private void clearMarked(IntArrayList marked) {
    for (int i = 0; i < marked.size(); i++) {
      markedFlags[marked.getInt(i)] = false;
    }
    marked.clear();
  }

  /**
   * The finished scan's outputs. As with the previous map-backed form, the underlying storage is
   * shared with the live {@link ScanResults} - a subsequent departure's scan invalidates it, so
   * results must be extracted before the next {@code scanRange} call.
   */
  public static final class ImmutableScanResult {
    private final ConnectionIndex kConnections;
    private final int[] bestArrivals;
    private final Stop[] indexToStop;

    ImmutableScanResult(ConnectionIndex kConnections, int[] bestArrivals, Stop[] indexToStop) {
      this.kConnections = kConnections;
      this.bestArrivals = bestArrivals;
      this.indexToStop = indexToStop;
    }

    public ConnectionIndex kConnections() {
      return kConnections;
    }

    /** Best arrivals as a Stop-keyed map, materialised on call - a test/diagnostic view, not hot. */
    public Map<Stop, Integer> bestArrivals() {
      final Map<Stop, Integer> view = HashMap.newHashMap(indexToStop.length);
      for (int i = 0; i < indexToStop.length; i++) {
        view.put(indexToStop[i], bestArrivals[i]);
      }
      return Collections.unmodifiableMap(view);
    }
  }
}
