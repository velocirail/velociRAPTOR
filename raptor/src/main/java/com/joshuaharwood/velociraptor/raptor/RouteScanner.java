package com.joshuaharwood.velociraptor.raptor;

import com.joshuaharwood.velociraptor.raptor.model.Trip;

/**
 * Finds the earliest catchable trip on a route, walking the route's trips backwards from a scan
 * position that only ever moves to earlier trips.
 * <p>
 * The position is valid for <b>one traversal of one route</b>. The paper scopes it that way:
 * "while traversing r we can find all et(r, .) values with a single sweep over this list, since
 * et(r, .) can only decrease". That holds because a trip is only looked for at a stop reached before
 * the trip already boarded departs it, so the answer is never a later trip than the one in hand. It
 * does not hold across traversals: a later round can enter the same route at an earlier stop, where
 * a trip departs sooner and meeting the same time needs a trip <em>after</em> the last one found.
 * Carrying the position over would start the sweep below that trip, read a departure that is too
 * early, and report no trip at all (upstream planarnetwork/raptor #52; BUGS.md 1.1). Callers must
 * therefore {@link #startRoute} before each traversal.
 * <p>
 * Callers must pre-filter the trips to only those running on the query service day - the scanner
 * trusts every trip it sees.
 */
public class RouteScanner {

  /** Returned by {@link #earliestTrip} when no trip on the route departs the stop at or after the time. */
  public static final int NO_TRIP = -1;

  private final Trip[][] tripsByRoute;

  // Index into tripsByRoute[currentRoute] of the last trip found on the traversal in progress.
  private int scanPosition = NO_TRIP;

  RouteScanner(Trip[][] tripsByRoute) {
    this.tripsByRoute = tripsByRoute;
  }

  /** Begins a traversal of {@code routeIdx}: the sweep starts again from its latest trip. */
  public void startRoute(int routeIdx) {
    scanPosition = tripsByRoute[routeIdx].length - 1;
  }

  /**
   * Whether the route picks up at this position. Pick-up is part of a route's signature, so every
   * trip on it shares the flag.
   */
  public boolean canBoard(int routeIdx, int position) {
    return tripsByRoute[routeIdx][0].canBoard(position);
  }

  /** The trip at this index on the route, as returned by {@link #earliestTrip}. */
  public Trip trip(int routeIdx, int tripIdx) {
    return tripsByRoute[routeIdx][tripIdx];
  }

  /**
   * The index of the earliest trip on the route departing this stop at or after {@code time}, or
   * {@link #NO_TRIP}. Never above the last index returned on this traversal. {@code routeIdx} must
   * be the route passed to the last {@link #startRoute}.
   */
  public int earliestTrip(int routeIdx, int stopIndex, int time) {
    int lastFound = NO_TRIP;
    final Trip[] routeTrips = tripsByRoute[routeIdx];

    // iterate backwards through the trips on the route, starting where we last found a trip
    for (int i = scanPosition; i >= 0; i--) {
      // if the trip is unreachable, exit the loop (int accessor so an OffsetTrip adds its day offset
      // without materialising stop times)
      if (routeTrips[i].departureTime(stopIndex) < time) {
        break;
      }

      lastFound = i;
      scanPosition = i;
    }

    return lastFound;
  }
}
