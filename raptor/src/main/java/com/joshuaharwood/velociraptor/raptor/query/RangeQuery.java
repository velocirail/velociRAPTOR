package com.joshuaharwood.velociraptor.raptor.query;

import com.joshuaharwood.velociraptor.raptor.FixedLinkRules;
import com.joshuaharwood.velociraptor.raptor.RangeScanScratch;
import com.joshuaharwood.velociraptor.raptor.RaptorAlgorithm;
import com.joshuaharwood.velociraptor.raptor.model.ScanResults;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.model.Transfer;
import com.joshuaharwood.velociraptor.raptor.result.ResultsFactory;
import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Range (profile) query - a behavioural 1-to-1 port of the TS reference's {@code RaptorTimeRangeQuery}.
 * <p>
 * Departure times at the origin are pre-filtered to the window and processed <b>latest to earliest</b>
 * over one reused {@link ScanResults}. {@link RaptorAlgorithm#scanRange} retains the per-round
 * {@code kArrivals} labels between departures (rRAPTOR's "keep the labels between runs", RAPTOR 2012
 * Section 4.2) and prunes with a per-round destination-arrival domination bound. Each departure keeps
 * a fresh connection index, so its journeys reconstruct self-consistently and are extracted before the
 * next departure's {@code scanRange} resets that index.
 * <p>
 * Two documented Java deviations from the TS reference: identical journeys reached from several departures
 * are de-duplicated via an {@code ObjectLinkedOpenHashSet} (the reference concatenates with duplicates),
 * and the output is reversed to earliest-departure-first to match the reference's {@code .reverse()}.
 */
public class RangeQuery<T> {
  private final RaptorAlgorithm raptor;
  private final ResultsFactory<T> resultsFactory;
  private final FixedLinkRules fixedLinkRules;

  public RangeQuery(RaptorAlgorithm raptor, ResultsFactory<T> resultsFactory) {
    this(raptor, resultsFactory, FixedLinkRules.NONE);
  }

  /** @param fixedLinkRules where a journey may use a fixed link; enforced inside the scan */
  public RangeQuery(RaptorAlgorithm raptor, ResultsFactory<T> resultsFactory, FixedLinkRules fixedLinkRules) {
    this.raptor = raptor;
    this.resultsFactory = resultsFactory;
    this.fixedLinkRules = fixedLinkRules;
  }

  public List<T> plan(Stop origin, Stop destination, LocalDate date, @Nullable Integer time, @Nullable Integer endTime) {
    return plan(origin, destination, date, time, endTime, Set.of());
  }

  public List<T> plan(Stop origin, Stop destination, LocalDate date, @Nullable Integer time, @Nullable Integer endTime, Set<Stop> notVias) {
    final int startSeconds = time == null ? Integer.MIN_VALUE : time;
    final int endSeconds = endTime == null ? Integer.MAX_VALUE : endTime;

    final int[] departures = departureTimes(origin, startSeconds, endSeconds);

    if (departures.length == 0) {
      return Collections.emptyList();
    }

    final ObjectLinkedOpenHashSet<T> results = new ObjectLinkedOpenHashSet<>();
    final ScanResults scanResults = raptor.scanResultsFactory().create(Map.of(origin, departures[0]));
    final RangeScanScratch scratch = raptor.createRangeScratch(notVias, fixedLinkRules);

    for (int departureTime : departures) {
      final var scan = raptor.scanRange(scanResults, scratch, Map.of(origin, departureTime), destination);
      results.addAll(resultsFactory.getResults(scan.kConnections(), destination, departureTime));
    }

    // The TS reference reverses the latest-first accumulation to earliest-departure-first.
    final List<T> ordered = new ArrayList<>(results);
    Collections.reverse(ordered);
    return List.copyOf(ordered);
  }

  /**
   * The departure times to iterate, latest first: rRAPTOR's Ψ (RAPTOR 2012 §4.2). Every trip
   * departure at the origin inside the window, and - unless a journey may not begin with a fixed
   * link - for each fixed link from the origin, every trip departure at its far end pushed back by
   * the link's duration and the far end's interchange, so that leaving the origin at that time
   * reaches the far end exactly as the trip departs. Seeding only the origin's own departures
   * (BUGS.md 6.3) never found a journey that starts with a fixed link to a neighbouring stop unless an
   * unrelated origin departure happened to fall early enough. One link deep, like the paper's Ψ.
   */
  private int[] departureTimes(Stop origin, int start, int end) {
    final IntRBTreeSet seeds = new IntRBTreeSet();
    for (int time : raptor.departureTimesAtStop().getOrDefault(origin, new int[0])) {
      if (time >= start && time < end) {
        seeds.add(time);
      }
    }
    if (!fixedLinkRules.forbidLeading()) {
      for (Transfer link : raptor.transfersFrom(origin)) {
        final int offset = link.duration() + raptor.interchangeTime(link.destination());
        for (int time : raptor.departureTimesAtStop().getOrDefault(link.destination(), new int[0])) {
          final int seed = time - offset;
          if (seed >= start && seed < end) {
            seeds.add(seed);
          }
        }
      }
    }
    final int[] departures = seeds.toIntArray();
    // The tree set is ascending; the scan wants latest first.
    for (int i = 0, j = departures.length - 1; i < j; i++, j--) {
      final int swap = departures[i];
      departures[i] = departures[j];
      departures[j] = swap;
    }
    return departures;
  }
}
