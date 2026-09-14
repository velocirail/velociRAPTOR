package com.joshuaharwood.velociraptor.raptor.model;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;

/**
 * Builds the route queue Q from the marked stops, int-indexed: for each route serving a marked
 * stop, the queue keeps the <b>position</b> of the earliest (lowest-position) marked stop on that
 * route - which is exactly the position the route scan starts from, so no further lookup is needed.
 *
 * @param routeStopPosition {@code [routeIdx] -> {stopIdx -> position}}, sparse map per route
 * @param routesAtStop      {@code [stopIdx] -> routeIdx[]} of the boardable routes at each stop
 */
public record QueueFactory(int[][] routesAtStop, Int2IntOpenHashMap[] routeStopPosition) {

  private static final int ABSENT_VAL = -1;

  /**
   * Fills the queue in place: {@code queuePositions[routeIdx]} is the scan start position for each
   * route in {@code queuedRoutes}, and {@code ABSENT_VAL} for every route not queued. Previously
   * queued routes are reset first, so the arrays can be reused across rounds.
   */
  public void fillQueue(int[] queuePositions, IntArrayList queuedRoutes, IntArrayList markedStops) {
    for (int i = 0; i < queuedRoutes.size(); i++) {
      queuePositions[queuedRoutes.getInt(i)] = ABSENT_VAL;
    }
    queuedRoutes.clear();

    for (int i = 0; i < markedStops.size(); i++) {
      final int stopIdx = markedStops.getInt(i);

      for (final int routeIdx : routesAtStop[stopIdx]) {
        final int position = routeStopPosition[routeIdx].get(stopIdx);
        final int queuedPosition = queuePositions[routeIdx];

        if (queuedPosition == ABSENT_VAL) {
          queuePositions[routeIdx] = position;
          queuedRoutes.add(routeIdx);
        } else if (position < queuedPosition) {
          queuePositions[routeIdx] = position;
        }
      }
    }
  }
}
