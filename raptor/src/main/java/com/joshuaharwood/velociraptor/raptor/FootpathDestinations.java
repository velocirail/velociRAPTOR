package com.joshuaharwood.velociraptor.raptor;

import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.model.Transfer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Brings the stops that footpaths lead to into the stop index alongside the stops routes call at.
 * <p>
 * The index is built from the routes running that day, so a stop with no service - a station with
 * no trains on the query date, say - was left out even when a fixed link led to it. A footpath into
 * an unindexed stop was skipped by the scan, so the journey that ends with that walk was never
 * found. Following every footpath from an indexed stop, and every footpath from the stops that
 * reach, gives those stops an index, an interchange time and an (empty) route list, so they can be
 * labelled, walked on from, and returned as destinations.
 */
public final class FootpathDestinations {

  private FootpathDestinations() {}

  /**
   * Adds every stop reachable by footpath from the indexed stops to the index-defining maps.
   *
   * @param indexedTransfers   footpaths by origin for every indexed stop; defines the stop index, and
   *                           gains an entry per footpath destination
   * @param transfersByStop    all footpaths by origin, consulted for the destinations' own footpaths
   * @param interchange        minimum interchange by stop; destinations without one get the default
   * @param defaultInterchange interchange time for a stop the feed gives none for
   * @param routesAtStop       boardable routes by stop; destinations gain an empty list
   */
  public static void addTo(Map<Stop, List<Transfer>> indexedTransfers,
                           Map<Stop, List<Transfer>> transfersByStop,
                           Map<Stop, Integer> interchange,
                           int defaultInterchange,
                           Map<Stop, List<String>> routesAtStop) {
    final Deque<Transfer> pending = new ArrayDeque<>();
    for (List<Transfer> transfers : indexedTransfers.values()) {
      pending.addAll(transfers);
    }

    while (!pending.isEmpty()) {
      final Stop destination = pending.pop().destination();
      if (indexedTransfers.containsKey(destination)) {
        continue;
      }
      final List<Transfer> onward = transfersByStop.getOrDefault(destination, List.of());
      indexedTransfers.put(destination, onward);
      interchange.putIfAbsent(destination, defaultInterchange);
      routesAtStop.putIfAbsent(destination, new ArrayList<>());
      pending.addAll(onward);
    }
  }
}
