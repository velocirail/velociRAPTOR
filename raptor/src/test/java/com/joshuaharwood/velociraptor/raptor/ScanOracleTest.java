package com.joshuaharwood.velociraptor.raptor;

import com.joshuaharwood.velociraptor.raptor.model.ResultConnectionIndex;
import com.joshuaharwood.velociraptor.raptor.model.ResultConnectionIndex.ResultConnection;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.model.StopTime;
import com.joshuaharwood.velociraptor.raptor.model.Transfer;
import com.joshuaharwood.velociraptor.raptor.model.Trip;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import static com.joshuaharwood.velociraptor.raptor.TestData.st;
import static com.joshuaharwood.velociraptor.raptor.TestData.stop;
import static com.joshuaharwood.velociraptor.raptor.TestData.t;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the scan against a brute-force oracle that shares none of its machinery: no routes, no
 * trip-scan cursor, no queue. Each round the oracle tries every trip at every boardable call whose
 * stop carries a label from the previous round, and every footpath from such a stop, and records an
 * improvement wherever the result beats the best arrival so far. That is the definition of the
 * per-round labels the scan is meant to compute, so agreement over many random timetables verifies
 * the trip-scan cursor reset and the overtaking split on their own terms rather than by reference
 * to another implementation.
 * <p>
 * The oracle mirrors two modelling choices of this port that are not under test: the destination's
 * interchange time is folded into an arrival on alighting, and a footpath is taken from the previous
 * round's labels, so a trip followed by a walk followed by a trip takes three rounds.
 * <p>
 * Random timetables draw trips from a few stop patterns with independent running and dwell times,
 * so trips on one pattern routinely overtake one another at some calls and not others, and footpaths
 * back to earlier stops make later rounds re-enter routes upstream of where an earlier round did.
 */
class ScanOracleTest {

  private static final int NOT_REACHED = Integer.MAX_VALUE;
  private static final int CASES = 3000;

  @Test
  void scanMatchesBruteForceOracleWithoutInterchangeTime() {
    for (int seed = 0; seed < CASES; seed++) {
      checkCase(seed, 0);
    }
  }

  /** Dwells are drawn independently of the interchange, so many are shorter than it. */
  @Test
  void scanMatchesBruteForceOracleWithInterchangeTime() {
    for (int seed = 0; seed < CASES; seed++) {
      checkCase(seed, 300);
    }
  }

  @Test
  void tripsOnARouteAreOrderedAtEveryCall() {
    for (int seed = 0; seed < CASES; seed++) {
      var data = Timetable.random(new Random(seed), 0);
      var sorted = new ArrayList<>(data.trips);
      sorted.sort(Comparator.comparingInt(trip -> trip.departureTime(0)));

      Map<String, List<Trip>> tripsByRoute = new HashMap<>();
      for (Trip trip : sorted) {
        tripsByRoute.computeIfAbsent(RouteGrouping.routeIdFor(trip.stopTimes(), tripsByRoute), _ -> new ArrayList<>())
                    .add(trip);
      }

      for (Map.Entry<String, List<Trip>> route : tripsByRoute.entrySet()) {
        List<Trip> trips = route.getValue();
        for (int i = 1; i < trips.size(); i++) {
          Trip earlier = trips.get(i - 1);
          Trip later = trips.get(i);
          for (int p = 0; p < earlier.stopTimes().size(); p++) {
            assertThat(later.arrivalTime(p))
              .as("seed %d route %s trips %d,%d arrival at call %d", seed, route.getKey(), i - 1, i, p)
              .isGreaterThanOrEqualTo(earlier.arrivalTime(p));
            assertThat(later.departureTime(p))
              .as("seed %d route %s trips %d,%d departure at call %d", seed, route.getKey(), i - 1, i, p)
              .isGreaterThanOrEqualTo(earlier.departureTime(p));
          }
        }
      }
    }
  }


  private static void checkCase(int seed, int interchangeTime) {
    var random = new Random(seed);
    var data = Timetable.random(random, interchangeTime);
    var origin = data.stops.get(random.nextInt(data.stops.size()));
    int time = random.nextInt(30_000);

    var raptor = TestRaptorBuilder.create(data.trips, data.transfers, new HashMap<>(data.interchange));
    var scan = raptor.scan(Map.of(origin, time));
    var oracle = Oracle.run(data, origin, time);

    for (Stop stop : data.stops) {
      assertThat(scan.bestArrivals().get(stop))
        .as("seed %d best arrival at %s", seed, stop.id())
        .isEqualTo(oracle.best.get(stop));

      Map<Integer, ResultConnectionIndex> connections = scan.kConnections().getOrDefault(stop, Map.of());
      Map<Integer, Integer> expectedRounds = oracle.improvements.getOrDefault(stop, Map.of());
      assertThat(connections.keySet())
        .as("seed %d rounds improving %s", seed, stop.id())
        .isEqualTo(expectedRounds.keySet());

      for (Map.Entry<Integer, ResultConnectionIndex> entry : connections.entrySet()) {
        if (entry.getValue() instanceof ResultConnection rc) {
          int arrival = rc.trip().arrivalTime(rc.endIndex()) + data.interchange.get(stop);
          assertThat(arrival)
            .as("seed %d round %d arrival at %s", seed, entry.getKey(), stop.id())
            .isEqualTo(expectedRounds.get(entry.getKey()));
        }
      }
    }
  }

  /** A random timetable: a handful of stop patterns, trips with independent running times, footpaths. */
  record Timetable(List<Stop> stops,
                           List<Trip> trips,
                           Map<Stop, List<Transfer>> transfers,
                           Map<Stop, Integer> interchange) {

    static Timetable random(Random random, int interchangeTime) {
      int stopCount = 5 + random.nextInt(4);
      List<Stop> pool = new ArrayList<>();
      for (int i = 0; i < stopCount; i++) {
        pool.add(stop("S" + i));
      }

      int patternCount = 2 + random.nextInt(3);
      List<List<Stop>> patterns = new ArrayList<>();
      for (int i = 0; i < patternCount; i++) {
        List<Stop> shuffled = new ArrayList<>(pool);
        java.util.Collections.shuffle(shuffled, random);
        patterns.add(shuffled.subList(0, 3 + random.nextInt(Math.min(3, stopCount - 2))));
      }

      int tripCount = 6 + random.nextInt(16);
      List<Trip> trips = new ArrayList<>();
      for (int i = 0; i < tripCount; i++) {
        List<Stop> pattern = patterns.get(random.nextInt(patterns.size()));
        StopTime[] stopTimes = new StopTime[pattern.size()];
        int clock = random.nextInt(40_000);
        for (int p = 0; p < pattern.size(); p++) {
          int arrival = clock;
          int departure = arrival + random.nextInt(600);
          String id = pattern.get(p).id();
          if (p == 0) {
            stopTimes[p] = st(id, null, departure);
          } else if (p == pattern.size() - 1) {
            stopTimes[p] = st(id, arrival, null);
          } else {
            stopTimes[p] = st(id, arrival, departure);
          }
          clock = departure + 300 + random.nextInt(2_700);
        }
        trips.add(t(stopTimes));
      }

      // Only stops that appear on a trip are in the algorithm's index.
      Set<Stop> used = new java.util.HashSet<>();
      for (Trip trip : trips) {
        for (StopTime stopTime : trip.stopTimes()) {
          used.add(stopTime.stop());
        }
      }
      List<Stop> stops = new ArrayList<>(pool.stream().filter(used::contains).toList());

      Map<Stop, List<Transfer>> transfers = new HashMap<>();
      int transferCount = 2 + random.nextInt(6);
      for (int i = 0; i < transferCount; i++) {
        Stop from = stops.get(random.nextInt(stops.size()));
        Stop to = stops.get(random.nextInt(stops.size()));
        if (from.equals(to)) {
          continue;
        }
        transfers.computeIfAbsent(from, _ -> new ArrayList<>())
                 .add(new Transfer(from, to, 60 + random.nextInt(3_600), 0, Integer.MAX_VALUE, null));
      }

      // Sometimes a stop no trip calls at, reached only by a footpath: it still gets labelled.
      List<Stop> unserved = pool.stream().filter(stop -> !used.contains(stop)).toList();
      if (!unserved.isEmpty() && random.nextBoolean()) {
        Stop walkOnly = unserved.getFirst();
        Stop from = stops.get(random.nextInt(stops.size()));
        transfers.computeIfAbsent(from, _ -> new ArrayList<>())
                 .add(new Transfer(from, walkOnly, 60 + random.nextInt(3_600), 0, Integer.MAX_VALUE, null));
        stops.add(walkOnly);
      }

      Map<Stop, Integer> interchange = new HashMap<>();
      for (Stop stop : stops) {
        interchange.put(stop, interchangeTime);
      }

      return new Timetable(stops, trips, transfers, interchange);
    }
  }

  /** Per-round labels computed by trying every trip and every footpath, with no shortcuts. */
  private record Oracle(Map<Stop, Integer> best, Map<Stop, Map<Integer, Integer>> improvements) {

    static Oracle run(Timetable data, Stop origin, int time) {
      Map<Stop, Integer> best = new HashMap<>();
      for (Stop stop : data.stops) {
        best.put(stop, NOT_REACHED);
      }
      best.put(origin, time);

      Map<Stop, Map<Integer, Integer>> improvements = new HashMap<>();
      Map<Stop, Integer> previous = Map.of(origin, time);

      for (int k = 1; !previous.isEmpty(); k++) {
        Map<Stop, Integer> current = new HashMap<>();

        for (Trip trip : data.trips) {
          List<StopTime> calls = trip.stopTimes();
          for (int i = 0; i < calls.size(); i++) {
            Integer label = previous.get(calls.get(i).stop());
            if (label == null || !calls.get(i).canBoard() || calls.get(i).departureTime() < label) {
              continue;
            }
            for (int j = i + 1; j < calls.size(); j++) {
              StopTime call = calls.get(j);
              if (call.canAlight()) {
                improve(best, current, call.stop(), call.arrivalTime() + data.interchange.get(call.stop()));
              }
            }
          }
        }

        for (Map.Entry<Stop, Integer> labelled : previous.entrySet()) {
          for (Transfer transfer : data.transfers.getOrDefault(labelled.getKey(), List.of())) {
            int arrival = labelled.getValue() + transfer.duration() + data.interchange.get(transfer.destination());
            if (transfer.startTime() <= arrival && arrival <= transfer.endTime()) {
              improve(best, current, transfer.destination(), arrival);
            }
          }
        }

        for (Map.Entry<Stop, Integer> improved : current.entrySet()) {
          improvements.computeIfAbsent(improved.getKey(), _ -> new TreeMap<>()).put(k, improved.getValue());
        }
        previous = current;
      }

      return new Oracle(best, improvements);
    }

    private static void improve(Map<Stop, Integer> best, Map<Stop, Integer> current, Stop stop, int arrival) {
      if (arrival < best.get(stop)) {
        best.put(stop, arrival);
        current.put(stop, arrival);
      }
    }
  }
}
