package com.joshuaharwood.velociraptor.raptor;

import com.joshuaharwood.velociraptor.raptor.model.QueueFactory;
import com.joshuaharwood.velociraptor.raptor.model.ScanResultsFactory;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.model.StopTime;
import com.joshuaharwood.velociraptor.raptor.model.Transfer;
import com.joshuaharwood.velociraptor.raptor.model.Trip;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a RaptorAlgorithm from raw trip data for testing.
 * Mirrors the TS RaptorQueryFactory.create() method.
 */
public final class TestRaptorBuilder {
  private static final int DEFAULT_INTERCHANGE_TIME = 0;

  private TestRaptorBuilder() {}

  /**
   * Create a RaptorAlgorithm from programmatic test data.
   *
   * @param trips       all trips running on the query day
   * @param transfers   transfers indexed by origin stop
   * @param interchange interchange times per stop
   */
  public static RaptorAlgorithm create(List<Trip> trips,
                                Map<Stop, List<Transfer>> transfers,
                                Map<Stop, Integer> interchange) {

    Map<Stop, List<String>> routesAtStop = new HashMap<>();
    Map<String, List<Trip>> tripsByRoute = new HashMap<>();
    Map<String, Map<Stop, Integer>> routeStopIndex = new HashMap<>();
    Map<String, List<Stop>> routePaths = new HashMap<>();
    Map<Stop, List<Transfer>> usefulTransfers = new HashMap<>();
    Map<Stop, Map<Integer, Integer>> departureTimeIndex = new HashMap<>();

    var sortedTrips = new ArrayList<>(trips);
    sortedTrips.sort(Comparator.comparingInt(trip -> trip.stopTimes().getFirst().departureTime()));

    for (Trip trip : sortedTrips) {
      List<StopTime> tripStopTimes = trip.stopTimes();
      List<Stop> path = tripStopTimes.stream().map(StopTime::stop).toList();
      String routeId = RouteGrouping.routeIdFor(tripStopTimes, tripsByRoute);

      if (!routeStopIndex.containsKey(routeId)) {
        tripsByRoute.put(routeId, new ArrayList<>());
        routeStopIndex.put(routeId, new HashMap<>());
        routePaths.put(routeId, path);

        for (int i = path.size() - 1; i >= 0; i--) {
          Stop stop = path.get(i);

          routeStopIndex.get(routeId).put(stop, i);
          usefulTransfers.put(stop, transfers.getOrDefault(stop, List.of()));
          interchange.putIfAbsent(stop, DEFAULT_INTERCHANGE_TIME);
          routesAtStop.putIfAbsent(stop, new ArrayList<>());

          if (tripStopTimes.get(i).canBoard()) {
            routesAtStop.get(stop).add(routeId);
          }
        }
      }

      tripsByRoute.get(routeId).add(trip);

      // Build departure time index for rRAPTOR (RAPTOR 2012 Section 4.2)
      for (StopTime stopTime : trip.stopTimes()) {
        if (stopTime.canBoard()) {
          departureTimeIndex.computeIfAbsent(stopTime.stop(), _ -> new HashMap<>())
                            .put(stopTime.departureTime(), stopTime.departureTime());
        }
      }
    }

    FootpathDestinations.addTo(usefulTransfers, transfers, interchange, DEFAULT_INTERCHANGE_TIME, routesAtStop);

    // Convert departure time index to sorted arrays (descending order per paper)
    Map<Stop, int[]> departureTimesAtStop = new HashMap<>(departureTimeIndex.size());
    for (Map.Entry<Stop, Map<Integer, Integer>> entry : departureTimeIndex.entrySet()) {
      int[] times = entry.getValue().values().stream()
                         .sorted(Comparator.reverseOrder())
                         .mapToInt(Integer::intValue)
                         .toArray();
      departureTimesAtStop.put(entry.getKey(), times);
    }

    // Build bidirectional int index mappings: the scan hot path works purely on these indices.
    Object2IntMap<Stop> stopToIndex = new Object2IntOpenHashMap<>(usefulTransfers.size());
    Stop[] indexToStop = new Stop[usefulTransfers.size()];
    stopToIndex.defaultReturnValue(-1);

    int stopIdx = 0;
    for (Stop stop : usefulTransfers.keySet()) {
      stopToIndex.put(stop, stopIdx);
      indexToStop[stopIdx] = stop;
      stopIdx++;
    }

    Object2IntMap<String> routeToIndex = new Object2IntOpenHashMap<>(tripsByRoute.size());
    routeToIndex.defaultReturnValue(-1);

    int routeIdx = 0;
    for (String routeId : tripsByRoute.keySet()) {
      routeToIndex.put(routeId, routeIdx);
      routeIdx++;
    }

    // Convert routeStopIndex: Map<String, Map<Stop, Integer>> -> Int2IntOpenHashMap[routeCount]
    // Keep sparse (each route only has ~20 stops), avoid OOM from dense 2D array
    final int stopCount = stopToIndex.size();
    final int routeCount = routeToIndex.size();
    final Int2IntOpenHashMap[] routeStopPositions = new Int2IntOpenHashMap[routeCount];

    for (Map.Entry<String, Map<Stop, Integer>> entry : routeStopIndex.entrySet()) {
      int rIdx = routeToIndex.getInt(entry.getKey());
      Int2IntOpenHashMap indexedStops = new Int2IntOpenHashMap(entry.getValue().size());
      for (Map.Entry<Stop, Integer> stopEntry : entry.getValue().entrySet()) {
        indexedStops.put(stopToIndex.getInt(stopEntry.getKey()), stopEntry.getValue().intValue());
      }
      routeStopPositions[rIdx] = indexedStops;
    }

    // Convert routesAtStop: Map<Stop, List<String>> -> int[stopCount][]
    final int[][] routesAtStopArray = new int[stopCount][];
    for (Map.Entry<Stop, List<String>> entry : routesAtStop.entrySet()) {
      int sIdx = stopToIndex.getInt(entry.getKey());
      List<String> stopRoutes = entry.getValue();
      int[] routeIndices = new int[stopRoutes.size()];
      for (int i = 0; i < stopRoutes.size(); i++) {
        routeIndices[i] = routeToIndex.getInt(stopRoutes.get(i));
      }
      routesAtStopArray[sIdx] = routeIndices;
    }

    // Convert routePaths: Map<String, List<Stop>> -> int[routeCount][]
    final int[][] routePathsArray = new int[routeCount][];
    for (Map.Entry<String, List<Stop>> entry : routePaths.entrySet()) {
      int rIdx = routeToIndex.getInt(entry.getKey());
      List<Stop> path = entry.getValue();
      int[] pathArray = new int[path.size()];
      for (int i = 0; i < path.size(); i++) {
        pathArray[i] = stopToIndex.getInt(path.get(i));
      }
      routePathsArray[rIdx] = pathArray;
    }

    // Convert transfers: Map<Stop, List<Transfer>> -> Transfer[stopCount][]
    final Transfer[][] transfersArray = new Transfer[stopCount][];
    for (Map.Entry<Stop, List<Transfer>> entry : usefulTransfers.entrySet()) {
      int sIdx = stopToIndex.getInt(entry.getKey());
      List<Transfer> stopTransfers = entry.getValue();
      transfersArray[sIdx] = stopTransfers.toArray(new Transfer[0]);
    }

    // Convert interchange: Map<Stop, Integer> -> int[stopCount]
    final int[] interchangeArray = new int[stopCount];
    for (Map.Entry<Stop, Integer> entry : interchange.entrySet()) {
      final int sIdx = stopToIndex.getInt(entry.getKey());
      // As the GTFS factory does: a stop on no route and at the end of no footpath is not indexed.
      if (sIdx >= 0) {
        interchangeArray[sIdx] = entry.getValue();
      }
    }

    return new RaptorAlgorithm(
      stopToIndex,
      routePathsArray,
      transfersArray,
      interchangeArray,
      departureTimesAtStop,
      new ScanResultsFactory(stopToIndex, indexToStop),
      new QueueFactory(routesAtStopArray, routeStopPositions),
      new RouteScannerFactory(tripsByRoute, routeToIndex)
    );
  }
}
