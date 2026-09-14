package com.joshuaharwood.velociraptor.raptor;

import com.joshuaharwood.velociraptor.raptor.model.StopTime;
import com.joshuaharwood.velociraptor.raptor.model.Trip;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Assigns trips to RAPTOR routes: trips with the same stop pattern (stops plus pick-up/set-down
 * flags) share a route, split where one trip overtakes another.
 * <p>
 * The scan walks a route's trips backwards and stops at the first that departs too early, which
 * is only sound if the trips are time-ordered at <em>every</em> call. Trips are therefore offered
 * in first-departure order, and a trip joins the first route of its signature whose latest trip it
 * does not overtake at any call; otherwise a new route is opened for it. Because no trip overtakes
 * the route it joins, the trip added last is the latest at every call and is the only one that has
 * to be compared against (upstream planarnetwork/raptor #51; BUGS.md 2.3). The older test compared
 * final arrivals only, and never compared trips already diverted onto the single "overtakes" route
 * against each other, so a route could still hold unordered trips.
 */
public final class RouteGrouping {

  private static final String OVERTAKING_ROUTE_SUFFIX = "|overtakes";

  private RouteGrouping() {}

  /**
   * The route id for a trip with these stop times, given the trips already assigned. Trips must be
   * offered in first-departure order.
   *
   * @param stopTimes    the trip's stop times
   * @param tripsByRoute trips already assigned, keyed by the route ids this method returned for them
   */
  public static String routeIdFor(List<StopTime> stopTimes, Map<String, List<Trip>> tripsByRoute) {
    final String signature = stopTimes.stream()
      .map(st -> st.stop().id()
                 + (st.canBoard() ? 1 : 0)
                 + (st.canAlight() ? 1 : 0))
      .collect(Collectors.joining(","));

    for (int n = 0; ; n++) {
      final String routeId = n == 0 ? signature : signature + OVERTAKING_ROUTE_SUFFIX + n;
      final List<Trip> routeTrips = tripsByRoute.get(routeId);

      if (routeTrips == null || routeTrips.isEmpty() || !overtakes(stopTimes, routeTrips.getLast())) {
        return routeId;
      }
    }
  }

  /** Whether a trip with these stop times arrives or departs before {@code latest} at any call. */
  private static boolean overtakes(List<StopTime> stopTimes, Trip latest) {
    // A shared signature means an identical stop pattern, hence the same length, so positions align.
    // The int accessors avoid materialising an OffsetTrip's stop times for the check.
    for (int p = 0; p < stopTimes.size(); p++) {
      final StopTime call = stopTimes.get(p);
      if (call.arrivalTime() < latest.arrivalTime(p) || call.departureTime() < latest.departureTime(p)) {
        return true;
      }
    }
    return false;
  }
}
