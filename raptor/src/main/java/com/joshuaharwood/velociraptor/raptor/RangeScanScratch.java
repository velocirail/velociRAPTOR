package com.joshuaharwood.velociraptor.raptor;

import it.unimi.dsi.fastutil.ints.IntArrayList;

/**
 * Per-query scratch for {@link RaptorAlgorithm#scanRange}, reused across the departures of one
 * range query. Re-creating these per departure was ~70% of allocation pressure under load (the
 * queue positions array and the not-via flags are O(routes) or O(stops), allocated per departure
 * of every request); reuse resets them instead.
 * <p>
 * Not thread-safe - one instance per in-flight query, like {@code ScanResults}.
 */
public final class RangeScanScratch {

  final int[] queuePositions;
  final IntArrayList queuedRoutes;
  final RouteScanner routeScanner;
  final boolean[] notVia;
  final FixedLinkRules rules;

  RangeScanScratch(int[] queuePositions, IntArrayList queuedRoutes, RouteScanner routeScanner, boolean[] notVia, FixedLinkRules rules) {
    this.queuePositions = queuePositions;
    this.queuedRoutes = queuedRoutes;
    this.routeScanner = routeScanner;
    this.notVia = notVia;
    this.rules = rules;
  }
}
