package com.joshuaharwood.velociraptor.obabridge;

import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.services.calendar.CalendarService;
import com.joshuaharwood.velociraptor.gtfs.FixedLink;
import com.joshuaharwood.velociraptor.gtfs.ExtendedGtfsRelationalDaoImpl;
import com.joshuaharwood.velociraptor.raptor.RaptorAlgorithm;
import com.joshuaharwood.velociraptor.raptor.FootpathDestinations;
import com.joshuaharwood.velociraptor.raptor.RouteGrouping;
import com.joshuaharwood.velociraptor.raptor.RouteScannerFactory;
import com.joshuaharwood.velociraptor.raptor.model.DefaultTrip;
import com.joshuaharwood.velociraptor.raptor.model.QueueFactory;
import com.joshuaharwood.velociraptor.raptor.model.ScanResultsFactory;
import com.joshuaharwood.velociraptor.raptor.model.PickupDropOffType;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.model.StopTime;
import com.joshuaharwood.velociraptor.raptor.model.Transfer;
import com.joshuaharwood.velociraptor.raptor.model.Trip;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds a {@link RaptorAlgorithm} from a OneBusAway GTFS DAO.
 * <p>
 * Maps OBA entities into raptor's slim record model and pre-filters trips to those running on the given service
 * date - raptor's {@code RouteScanner} trusts every trip it sees runs that day.
 * <p>
 * Builds the departure time index for rRAPTOR (RAPTOR 2012, Section 4.2), accumulating unique
 * departure times at each stop where boarding is allowed. This enables pre-filtering in range queries.
 */
public final class RaptorAlgorithmFactory {
  private static final int DEFAULT_INTERCHANGE_TIME = 0;


  public static final int ABSENT_VAL = -1;

  private RaptorAlgorithmFactory() {
  }

  public static RaptorAlgorithm createFromDao(ExtendedGtfsRelationalDaoImpl dao,
                                              CalendarService calendarService,
                                              ServiceDate serviceDate) {
    return createFromDao(dao, calendarService, serviceDate,
            (obaTrip, stopTimes) -> new DefaultTrip(obaTrip.getId().getId(), stopTimes));
  }

  public static RaptorAlgorithm createFromDao(ExtendedGtfsRelationalDaoImpl dao,
                                              CalendarService calendarService,
                                              ServiceDate serviceDate,
                                              BiFunction<org.onebusaway.gtfs.model.Trip, List<StopTime>, Trip> tripFactory) {
    final Function<org.onebusaway.gtfs.model.Stop, Stop> toStop = stopLookup(dao);
    final List<Trip> trips = tripsOnServiceDate(dao, calendarService, serviceDate, toStop, tripFactory);
    return assemble(dao, toStop, trips, serviceDate);
  }

  /** Memoise OBA Stop -> raptor Stop so every caller sees the same Stop instance per id. */
  private static Function<org.onebusaway.gtfs.model.Stop, Stop> stopLookup(ExtendedGtfsRelationalDaoImpl dao) {
    final Map<String, Stop> stopLookup = new HashMap<>(dao.getAllStops().size());
    return obaStop -> stopLookup.computeIfAbsent(obaStop.getId().getId(), Stop::new);
  }

  /** Active trips on {@code serviceDate}, converted to raptor {@link Trip}s. */
  private static List<Trip> tripsOnServiceDate(ExtendedGtfsRelationalDaoImpl dao,
                                               CalendarService calendarService,
                                               ServiceDate serviceDate,
                                               Function<org.onebusaway.gtfs.model.Stop, Stop> toStop,
                                               BiFunction<org.onebusaway.gtfs.model.Trip, List<StopTime>, Trip> tripFactory) {
    // Active-service filter lives here (not in RouteScanner): the raptor core trusts every trip it sees runs that day.
    return calendarService.getServiceIdsOnDate(serviceDate).stream()
      .map(dao::getTripsForServiceId)
      .flatMap(Collection::stream)
      .map(obaTrip -> toTrip(obaTrip, dao, toStop, tripFactory))
      .toList();
  }

  /**
   * Shared assembly: derive transfers and interchange times, then build the route indices and arrays.
   * Trips are sorted by first departure so {@link RouteGrouping}'s overtaking check sees earlier-arriving
   * trips first (for the flattened timetable this also orders day k before day k+1).
   */
  private static RaptorAlgorithm assemble(ExtendedGtfsRelationalDaoImpl dao,
                                          Function<org.onebusaway.gtfs.model.Stop, Stop> toStop,
                                          List<Trip> trips,
                                          ServiceDate serviceDate) {
    final int stopCount = dao.getAllStops().size();
    final int routeCount = dao.getAllRoutes().size();

    // Fixed links carry their own day-of-week flags, start/end dates and a wall-clock time window.
    // Arrivals are seconds since the service date's midnight and run past 24:00 for the late trains,
    // so a link is offered twice: once for the service date with its window as published, and once
    // for the following calendar day with the window shifted by a day - so an arrival at 24:08 is
    // matched against the next day's flags and 00:01-23:59 becomes 24:01-47:59 (BUGS.md 7.2). A link
    // that runs on neither day is not offered at all, mirroring the active-service filter on trips.
    final Map<Stop, List<Transfer>> transfersByStop = new HashMap<>();
    for (FixedLink link : dao.getFixedLinksRunningOnServiceDay(serviceDate)) {
      transfersByStop.computeIfAbsent(toStop.apply(link.getFromStop()), _ -> new ArrayList<>())
                     .add(toTransfer(link, toStop, 0));
    }
    for (FixedLink link : dao.getFixedLinksRunningOnServiceDay(serviceDate.next())) {
      transfersByStop.computeIfAbsent(toStop.apply(link.getFromStop()), _ -> new ArrayList<>())
                     .add(toTransfer(link, toStop, SECONDS_PER_DAY));
    }

    final Map<Stop, Integer> interchangeTimes = dao.getAllTransfers().stream()
      .collect(Collectors.toMap(t -> toStop.apply(t.getFromStop()),
        org.onebusaway.gtfs.model.Transfer::getMinTransferTime));

    // Sort by first departure via the int accessor (not stopTimes().getFirst()): for an OffsetTrip
    // that is a pure int add, so the O(n log n) comparisons don't each materialise an offset stop-time
    // list. Equivalent to stopTimes().getFirst().departureTime() for every Trip impl.
    final List<Trip> sortedTrips = trips.stream()
      .sorted(Comparator.comparingInt(t -> t.departureTime(0)))
      .toList();

    return build(sortedTrips, transfersByStop, interchangeTimes, stopCount, routeCount);
  }

  private static RaptorAlgorithm build(List<Trip> trips,
                                       Map<Stop, List<Transfer>> transfersByStop,
                                       Map<Stop, Integer> interchangeTimes,
                                       int stopCount,
                                       int routeCount) {
    final Map<Stop, List<String>> routesAtStop = new HashMap<>(stopCount);
    final Map<String, List<Trip>> tripsByRoute = new HashMap<>(routeCount);
    final Map<String, Map<Stop, Integer>> routeStopIndex = new HashMap<>(routeCount);
    final Map<String, List<Stop>> routePaths = new HashMap<>(routeCount);
    final Map<Stop, List<Transfer>> usefulTransfers = new HashMap<>(stopCount);
    final Map<Stop, Map<Integer, Integer>> departureTimeIndex = new HashMap<>(stopCount);

    for (final Trip trip : trips) {
      // Materialise the trip's stop times once. For an OffsetTrip this builds the offset list a single
      // time per trip rather than on every accessor call below.
      final List<StopTime> stopTimes = trip.stopTimes();
      final List<Stop> path = stopTimes.stream().map(StopTime::stop).toList();
      final String routeId = RouteGrouping.routeIdFor(stopTimes, tripsByRoute);

      if (!routeStopIndex.containsKey(routeId)) {
        tripsByRoute.put(routeId, new ArrayList<>());
        routeStopIndex.put(routeId, new HashMap<>());
        routePaths.put(routeId, path);

        for (int i = path.size() - 1; i >= 0; --i) {
          Stop stop = path.get(i);

          routeStopIndex.get(routeId).put(stop, i);
          usefulTransfers.put(stop, transfersByStop.getOrDefault(stop, List.of()));
          interchangeTimes.putIfAbsent(stop, DEFAULT_INTERCHANGE_TIME);
          routesAtStop.putIfAbsent(stop, new ArrayList<>());

          if (stopTimes.get(i).canBoard()) {
            routesAtStop.get(stop).add(routeId);
          }
        }
      }

      tripsByRoute.get(routeId).add(trip);

      // Build departure time index for rRAPTOR (RAPTOR 2012, Section 4.2)
      // Paper: "accumulate into a set Ψ all departure times of trips t at the source stop p_s"
      // We pre-compute this for ALL stops, not just at query time, matching the TypeScript
      // reference implementation (RaptorQueryFactory.ts:155-166).
      for (StopTime stopTime : stopTimes) {
        if (stopTime.canBoard()) {
          departureTimeIndex.computeIfAbsent(stopTime.stop(), _ -> new HashMap<>())
                            .put(stopTime.departureTime(), stopTime.departureTime());
        }
      }
    }

    // A stop with no service that day but a fixed link into it is still a destination.
    FootpathDestinations.addTo(usefulTransfers, transfersByStop, interchangeTimes, DEFAULT_INTERCHANGE_TIME, routesAtStop);

    // Convert departure time index to sorted arrays (descending order per paper)
    // Paper: "order Ψ from latest to earliest"
    // Using int[] instead of List<Integer> for memory efficiency (no boxing).
    final Map<Stop, int[]> departureTimesAtStop = new HashMap<>(departureTimeIndex.size());
    for (Map.Entry<Stop, Map<Integer, Integer>> entry : departureTimeIndex.entrySet()) {
      int[] times = entry.getValue().values().stream()
                         .sorted(Comparator.reverseOrder())
                         .mapToInt(Integer::intValue)
                         .toArray();
      departureTimesAtStop.put(entry.getKey(), times);
    }

    // Build bidirectional int index mappings: the scan hot path works purely on these indices.
    final Object2IntMap<Stop> stopToIndex = new Object2IntOpenHashMap<>(usefulTransfers.size());
    final Stop[] indexToStop = new Stop[usefulTransfers.size()];
    stopToIndex.defaultReturnValue(ABSENT_VAL); // Return -1 for missing keys instead of 0

    int stopIdx = 0;
    for (Stop stop : usefulTransfers.keySet()) {
      stopToIndex.put(stop, stopIdx);
      indexToStop[stopIdx] = stop;
      stopIdx++;
    }

    final Object2IntMap<String> routeToIndex = new Object2IntOpenHashMap<>(tripsByRoute.size());
    routeToIndex.defaultReturnValue(ABSENT_VAL);

    int routeIdx = 0;
    for (String routeId : tripsByRoute.keySet()) {
      routeToIndex.put(routeId, routeIdx);
      routeIdx++;
    }

    // === routeStopIndex: Map<String, Map<Stop, Integer>> -> Int2IntOpenHashMap[routeCount] ===
    //
    // This structure answers: "For route R, at what position is stop S?" (queue filling only).
    // Kept sparse (map-per-route) to avoid OOM: each route typically serves ~20 stops out of ~2000 total.
    final int indexedStopCount = stopToIndex.size();
    final int indexedRouteCount = routeToIndex.size();
    final Int2IntOpenHashMap[] routeStopPositions = new Int2IntOpenHashMap[indexedRouteCount];

    for (Map.Entry<String, Map<Stop, Integer>> entry : routeStopIndex.entrySet()) {
      int rIdx = routeToIndex.getInt(entry.getKey());
      Int2IntOpenHashMap indexedStops = new Int2IntOpenHashMap(entry.getValue().size());
      for (Map.Entry<Stop, Integer> stopEntry : entry.getValue().entrySet()) {
        indexedStops.put(stopToIndex.getInt(stopEntry.getKey()), stopEntry.getValue().intValue());
      }
      routeStopPositions[rIdx] = indexedStops;
    }

    // === routesAtStop: Map<Stop, List<String>> -> int[stopCount][] ===
    // This structure answers: "Which routes can be boarded at stop S?" (queue filling only).
    final int[][] routesAtStopArray = new int[indexedStopCount][];
    for (Map.Entry<Stop, List<String>> entry : routesAtStop.entrySet()) {
      int sIdx = stopToIndex.getInt(entry.getKey());
      List<String> stopRoutes = entry.getValue();
      int[] routeIndices = new int[stopRoutes.size()];
      for (int i = 0; i < stopRoutes.size(); i++) {
        routeIndices[i] = routeToIndex.getInt(stopRoutes.get(i));
      }
      routesAtStopArray[sIdx] = routeIndices;
    }

    // === routePaths: Map<String, List<Stop>> -> int[routeCount][] ===
    // This structure answers: "For route R, what stop is at position P?"
    final int[][] routePathsArray = new int[indexedRouteCount][];
    for (Map.Entry<String, List<Stop>> entry : routePaths.entrySet()) {
      int rIdx = routeToIndex.getInt(entry.getKey());
      List<Stop> path = entry.getValue();
      int[] pathArray = new int[path.size()];
      for (int i = 0; i < path.size(); i++) {
        pathArray[i] = stopToIndex.getInt(path.get(i));
      }
      routePathsArray[rIdx] = pathArray;
    }

    // === transfers: Map<Stop, List<Transfer>> -> Transfer[stopCount][] ===
    // This structure answers: "What transfers are available from stop S?"
    final Transfer[][] transfersArray = new Transfer[indexedStopCount][];
    for (Map.Entry<Stop, List<Transfer>> entry : usefulTransfers.entrySet()) {
      int sIdx = stopToIndex.getInt(entry.getKey());
      List<Transfer> transfers = entry.getValue();
      transfersArray[sIdx] = transfers.toArray(new Transfer[0]);
    }

    // === interchange: Map<Stop, Integer> -> int[stopCount] ===
    // This structure answers: "What is the minimum interchange time at stop S?"
    // interchangeTimes may contain stops not in stopToIndex (stops not on any route), skip those.
    final int[] interchangeArray = new int[indexedStopCount];
    for (Map.Entry<Stop, Integer> entry : interchangeTimes.entrySet()) {
      int sIdx = stopToIndex.getInt(entry.getKey());
      if (sIdx != ABSENT_VAL) {
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
      new RouteScannerFactory(tripsByRoute, routeToIndex));
  }

  private static Trip toTrip(org.onebusaway.gtfs.model.Trip obaTrip,
                             ExtendedGtfsRelationalDaoImpl dao,
                             Function<org.onebusaway.gtfs.model.Stop, Stop> toStop,
                             BiFunction<org.onebusaway.gtfs.model.Trip, List<StopTime>, Trip> tripFactory) {
    List<StopTime> stopTimes = dao.getStopTimesForTrip(obaTrip).stream()
      .map(st -> new StopTime(toStop.apply((org.onebusaway.gtfs.model.Stop) st.getStop()),
        st.getArrivalTime(),
        st.getDepartureTime(),
        PickupDropOffType.fromGtfs(st.getPickupType()),
        PickupDropOffType.fromGtfs(st.getDropOffType())))
      .toList();
    return tripFactory.apply(obaTrip, stopTimes);
  }

  private static final int SECONDS_PER_DAY = 24 * 60 * 60;

  /** @param dayOffsetSeconds shifts the link's wall-clock window onto the service date's time-line */
  private static Transfer toTransfer(FixedLink link, Function<org.onebusaway.gtfs.model.Stop, Stop> toStop, int dayOffsetSeconds) {
    return new Transfer(toStop.apply((org.onebusaway.gtfs.model.Stop) link.getFromStop()),
      toStop.apply((org.onebusaway.gtfs.model.Stop) link.getToStop()),
      link.getDurationInSeconds(),
      link.getStartTime() + dayOffsetSeconds,
      link.getEndTime() + dayOffsetSeconds,
      link.getMode());
  }

}
