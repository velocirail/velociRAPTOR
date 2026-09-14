package com.joshuaharwood.velociraptor.raptor.tsport;

import com.joshuaharwood.velociraptor.raptor.model.QueueFactory;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1:1 port of {@code ext/planarnetwork-raptor/test/unit/raptor/QueueFactory.spec.ts}.
 * <p>
 * The Java {@link QueueFactory} is int-indexed and fills a supplied queue in place
 * ({@code fillQueue}) rather than returning one ({@code getQueue}). The queue holds the selected
 * stop's <b>position</b> on the route rather than the stop itself (that position is where the route
 * scan starts), so the spec's route→stop assertions become route→position-of-that-stop; the
 * earliest-stop selection is order-independent, so the assertions hold regardless of iteration
 * order.
 */
class QueueFactorySpecTest {

  private static final int STOP_A = 0;
  private static final int STOP_B = 1;

  private static final int ROUTE_A = 0;
  private static final int ROUTE_B = 1;
  private static final int ROUTE_C = 2;

  private static final int[][] ROUTES_AT_STOP = {
    {ROUTE_A, ROUTE_B}, // StopA
    {ROUTE_B, ROUTE_C}  // StopB
  };

  @Test
  void enqueuesStops() {
    var routeStopPosition = routeStopPosition(
      positions(STOP_A, 1),             // RouteA
      positions(STOP_A, 2, STOP_B, 1),  // RouteB
      positions(STOP_B, 1)              // RouteC
    );

    var queuePositions = fill(routeStopPosition, STOP_A, STOP_B);

    // RouteA from StopA (position 1), RouteB from StopB (position 1), RouteC from StopB (position 1).
    assertThat(queuePositions).containsExactly(1, 1, 1);
  }

  @Test
  void picksTheEarliestStopOnTheRoute() {
    var routeStopPosition = routeStopPosition(
      positions(STOP_A, 1),             // RouteA
      positions(STOP_A, 1, STOP_B, 2),  // RouteB
      positions(STOP_B, 1)              // RouteC
    );

    var queuePositions = fill(routeStopPosition, STOP_B, STOP_A);

    // RouteB picks StopA (position 1) over the earlier-marked StopB (position 2).
    assertThat(queuePositions).containsExactly(1, 1, 1);
  }

  private static int[] fill(Int2IntOpenHashMap[] routeStopPosition, int... marked) {
    var factory = new QueueFactory(ROUTES_AT_STOP, routeStopPosition);
    var queuePositions = new int[]{-1, -1, -1};
    var queuedRoutes = new IntArrayList();

    factory.fillQueue(queuePositions, queuedRoutes, IntArrayList.of(marked));

    assertThat(queuedRoutes.toIntArray()).containsExactlyInAnyOrder(ROUTE_A, ROUTE_B, ROUTE_C);
    return queuePositions;
  }

  private static Int2IntOpenHashMap[] routeStopPosition(Int2IntOpenHashMap... routes) {
    return routes;
  }

  private static Int2IntOpenHashMap positions(int... stopThenPosition) {
    var positions = new Int2IntOpenHashMap(stopThenPosition.length / 2);
    for (int i = 0; i < stopThenPosition.length; i += 2) {
      positions.put(stopThenPosition[i], stopThenPosition[i + 1]);
    }
    return positions;
  }
}
