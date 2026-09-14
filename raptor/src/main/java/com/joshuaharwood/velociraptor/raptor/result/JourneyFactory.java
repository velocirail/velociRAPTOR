package com.joshuaharwood.velociraptor.raptor.result;

import com.joshuaharwood.velociraptor.raptor.model.Leg;
import com.joshuaharwood.velociraptor.raptor.model.Leg.TimetableLeg;
import com.joshuaharwood.velociraptor.raptor.model.Leg.TransferLeg;
import com.joshuaharwood.velociraptor.raptor.model.ResultConnectionIndex;
import com.joshuaharwood.velociraptor.raptor.model.ResultConnectionIndex.ResultConnection;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.model.StopTime;
import com.joshuaharwood.velociraptor.raptor.model.Trip;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JourneyFactory implements ResultsFactory<Journey> {

  /**
   * Range (rRAPTOR) result extraction: one journey per round the destination was improved in, dropping
   * any whose first leg departs after the iterated departure ({@code currentSearchTime}). Every round
   * is kept - a round-k arrival is only ever recorded when it beats every arrival with fewer legs, so
   * each journey here is a genuine trade of more legs for an earlier arrival, and there is no cap on
   * the number of legs (the TS reference's range factory dropped journeys of 5 or more rounds, a
   * product filter that also counted footpaths, which take a round of their own in this port). A leading fixed link is rendered at the search time (so it
   * always passes the filter), mirroring the reference's {@code getJourneyLegs} leading-transfer
   * anchoring; the 2-arg {@link #apply} path (depart-after) keeps the planarnetwork base behaviour.
   */
  @Override
  public List<Journey> getResults(Map<Stop, Map<Integer, ResultConnectionIndex>> kConnections,
                                  Stop destination,
                                  @Nullable Integer currentSearchTime) {
    final var results = new ArrayList<Journey>();

    // The TS reference iterates Object.keys(kConnections[dest]) in ascending round order; sort to match
    // (a hash-ordered map would emit journeys in a different order).
    final var rounds = new ArrayList<>(kConnections.getOrDefault(destination, Collections.emptyMap()).keySet());
    Collections.sort(rounds);

    for (Integer k : rounds) {
      final List<Leg> legs = getJourneyLegs(kConnections, k, destination);
      if (!hasTimetableLeg(legs)) {
        continue;
      }

      final Leg firstLeg = legs.getFirst();
      // A journey departs when its first train does, or - behind a leading fixed link - at the latest
      // time the link can be started and still make that train. (The TS reference anchored a leading
      // link on the iterated departure instead, which was only the seed that happened to find it, so
      // the same journey found from two seeds looked like two journeys leaving at different times.)
      final int departureTime = getDepartureTime(legs);

      // The TS reference's upper-bound filter: drop a journey whose first leg departs after the
      // iterated departure, so each train-first journey is reported from its own departure's seed.
      // A leading fixed link always passes: its latest start is at or after the seed that found it.
      if (currentSearchTime == null || firstLeg instanceof TransferLeg || departureTime <= currentSearchTime) {
        results.add(new Journey(legs, departureTime, getArrivalTime(legs)));
      }
    }

    return results;
  }

  @Override
  public List<Journey> apply(Map<Stop, Map<Integer, ResultConnectionIndex>> kConnections, Stop destination) {

    return kConnections.getOrDefault(destination, Collections.emptyMap())
                       .keySet()
                       .stream()
                       .sorted() // the TS reference's Object.keys order: ascending round number
                       .map(k -> getJourneyLegs(kConnections, k, destination))
                       .filter(JourneyFactory::hasTimetableLeg)
                       .map(legs -> new Journey(legs, getDepartureTime(legs), getArrivalTime(legs)))
                       .toList();
  }

  /**
   * A journey has to ride something. One made only of fixed links - a walk from the origin to the
   * destination - is not a public transport journey and has no timetabled times to report, so it is
   * not returned.
   */
  private static boolean hasTimetableLeg(List<Leg> legs) {
    for (Leg leg : legs) {
      if (leg instanceof TimetableLeg) {
        return true;
      }
    }
    return false;
  }

  private List<Leg> getJourneyLegs(Map<Stop, Map<Integer, ResultConnectionIndex>> kConnections,
                                   Integer k,
                                   Stop finalDestination) {
    var destination = finalDestination;

    final var legs = new ArrayList<Leg>();

    int i = k;
    while (i > 0) {
      // Range-mode label retention (RaptorAlgorithm.scanRange / ScanResults.addRangeRound) copies a
      // stop's arrival forward into later rounds without re-recording its connection, and the origin
      // seed never has a connection at all. So a parent leg can live at a round earlier than i, or -
      // for the origin seed - not be present at any round. Walk down to the parent's actual round; if
      // the stop has no connection at or below i, the chain has reached the origin seed and the
      // journey is complete. (The strict 1:1 TS port indexes kConnections[destination][i] directly,
      // and throws an NPE here when that entry is absent.)
      final Map<Integer, ResultConnectionIndex> stopConnections = kConnections.get(destination);
      ResultConnectionIndex connection = null;
      while (stopConnections != null && i > 0 && (connection = stopConnections.get(i)) == null) {
        i--;
      }
      if (connection == null) {
        break;
      }

      destination = switch (connection) {
        case ResultConnection(Trip trip, int startIndex, int endIndex) -> {
          final List<StopTime> stopTimes = trip.stopTimes().subList(startIndex, endIndex + 1);
          final Stop origin = stopTimes.getFirst().stop();

          legs.add(new TimetableLeg(origin, destination, stopTimes, trip));

          yield origin;
        }
        case TransferLeg transferLeg -> {
          legs.add(transferLeg);
          yield transferLeg.origin();
        }
      };
      i--;
    }

    return legs.reversed();
  }

  private int getDepartureTime(List<Leg> legs) {
    int transferDuration = 0;

    for (Leg leg : legs) {
      switch (leg) {
        case TimetableLeg timetableLeg -> {
          return timetableLeg.stopTimes().getFirst().departureTime() - transferDuration;
        }
        // A leading fixed link: back its duration and far-end interchange off the onward departure,
        // giving the latest time the link can be started and still make the train.
        case TransferLeg transferLeg ->
          transferDuration += transferLeg.duration() + transferLeg.destinationInterchange();
      }
    }

    return 0;
  }

  private int getArrivalTime(List<Leg> legs) {
    int transferDuration = 0;

    for (int i = legs.size() - 1; i >= 0; i--) {
      switch (legs.get(i)) {
        case TimetableLeg timetableLeg -> {
          return timetableLeg.stopTimes().getLast().arrivalTime() + transferDuration;
        }
        // Matches the TS reference's rendered trailing fixed-link arrival:
        // previousRailArrival + originInterchange + duration.
        case TransferLeg transferLeg ->
          transferDuration += transferLeg.duration() + transferLeg.originInterchange();
      }
    }

    return 0;
  }
}
