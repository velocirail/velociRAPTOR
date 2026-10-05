package com.joshuaharwood.velociraptor.rail;

import com.joshuaharwood.velociraptor.raptor.model.Leg.TransferLeg;
import com.joshuaharwood.velociraptor.raptor.model.ResultConnectionIndex;
import com.joshuaharwood.velociraptor.raptor.model.ResultConnectionIndex.ResultConnection;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.model.StopTime;
import com.joshuaharwood.velociraptor.raptor.model.Trip;
import com.joshuaharwood.velociraptor.raptor.result.ResultsFactory;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RailJourneyFactory implements ResultsFactory<RailJourney> {

  private final LocalDate startDate;

  public RailJourneyFactory(LocalDate startDate) {
    this.startDate = startDate;
  }

  @Override
  public List<RailJourney> apply(Map<Stop, Map<Integer, ResultConnectionIndex>> kConnections, Stop destination) {
    return getResults(kConnections, destination, null);
  }

  @Override
  public List<RailJourney> getResults(Map<Stop, Map<Integer, ResultConnectionIndex>> kConnections, Stop destination, @Nullable Integer currentSearchTime) {
    List<RailJourney> results = new ArrayList<>();

    Map<Integer, ResultConnectionIndex> connectionsForStop = kConnections.getOrDefault(destination, Collections.emptyMap());

    // Build the lower bound the same way leg times are built (toDateTime), so a >24h departure
    // such as 25:30 becomes startDate+1 01:30 - not 01:30 on startDate. LocalDateTime.plusSeconds
    // rolls into the next day; LocalTime.plusSeconds would wrap and silently mis-bound it.
    LocalDateTime currentIterDateTime = currentSearchTime == null ? null : toDateTime(currentSearchTime);

    // The TS reference iterates Object.keys(kConnections[dest]), which yields the round numbers in ascending
    // order; a hash-ordered map iteration would emit journeys in a different order. Sort to match.
    List<Integer> rounds = new ArrayList<>(connectionsForStop.keySet());
    Collections.sort(rounds);

    // Every round is kept: a round-k arrival is only recorded when it beats every arrival with fewer
    // legs, so each is a genuine trade of more legs for an earlier arrival. (The TS reference's
    // detailed factory dropped journeys of 5 or more rounds; that cap also counted footpaths, which
    // take a round of their own in this port, and was applied to depart-after queries too.)
    for (int k : rounds) {
      List<Leg> legs = getJourneyLegs(kConnections, k, destination);
      // A journey made only of fixed links rides no train and is not returned.
      if (legs.stream().anyMatch(leg -> leg instanceof Leg.RailLeg)) {
        Leg firstLeg = legs.getFirst();

        // The TS reference's detailed journey factory upper-bound filter: keep a journey whose first leg departs at or
        // before this iteration's anchored departure (a transfer first leg is kept unconditionally).
        if (currentIterDateTime == null
                || firstLeg instanceof Leg.FixedLink
                || !((Leg.RailLeg) firstLeg).departureTime().isAfter(currentIterDateTime)) {
          results.add(new RailJourney(firstLeg.origin(), destination, legs));
        }
      }
    }
    return results;
  }

  private List<Leg> getJourneyLegs(Map<Stop, Map<Integer, ResultConnectionIndex>> kConnections,
                                   int k,
                                   Stop finalDestination) {
    List<Untimed> legs = new ArrayList<>();
    Stop previousOrigin = finalDestination;

    int i = k;
    while (i > 0) {
      // Range-mode label retention (RaptorAlgorithm.scanRange / ScanResults.addRangeRound) copies a
      // stop's arrival forward into later rounds without re-recording its connection, and the origin
      // seed never has a connection at all. So a parent leg can live at a round earlier than i, or -
      // for the origin seed - not be present at any round. Walk down to the parent's actual round; if
      // the stop has no connection at or below i, the chain has reached the origin seed and the
      // journey is complete. (The strict 1:1 TS port indexes kConnections[previousOrigin][i] directly,
      // and throws an NPE here when that entry is absent.)
      Map<Integer, ResultConnectionIndex> stopConnections = kConnections.get(previousOrigin);
      ResultConnectionIndex connection = null;
      while (stopConnections != null && i > 0 && (connection = stopConnections.get(i)) == null) {
        i--;
      }
      if (connection == null) {
        break;
      }

      switch (connection) {
        case ResultConnection(Trip trip, int startIndex, int endIndex) -> {
          // A multi-day overlay presents later service days as OffsetTrip wrappers over the base
          // RailTrip. Unwrap to recover the RailTrip metadata (trains/operator), but read
          // times from the trip's own stopTimes() so they carry any day offset (i.e. are absolute
          // from the start date) - OffsetTrip materialises offset stop times for reconstruction.
          RailTrip railTrip = railTripOf(trip);
          List<StopTime> stopTimes = trip.stopTimes();
          StopTime originStopTime = stopTimes.get(startIndex);
          StopTime destinationStopTime = stopTimes.get(endIndex);

          Stop origin = originStopTime.stop();
          Stop destination = destinationStopTime.stop();

          legs.add(new Train(new Leg.RailLeg(
                  origin,
                  destination,
                  toDateTime(originStopTime.departureTime()),
                  toDateTime(destinationStopTime.arrivalTime()),
                  toService(railTrip.boardingTrain(startIndex)),
                  toService(railTrip.alightingTrain(endIndex)),
                  toTrainTrip(railTrip, stopTimes),
                  startIndex,
                  endIndex
          )));
          previousOrigin = origin;
        }
        case TransferLeg link -> {
          // Timed by withFixedLinkTimes once the neighbouring train legs are known.
          legs.add(new Link(link));
          previousOrigin = link.origin();
        }
      }
      i--;
    }

    return withFixedLinkTimes(legs.reversed());
  }

  /** A leg as the scan records it: a train leg, or a fixed link whose times follow from the trains around it. */
  private sealed interface Untimed permits Train, Link {}

  private record Train(Leg.RailLeg leg) implements Untimed {}

  private record Link(TransferLeg link) implements Untimed {}

  /**
   * A fixed link has no timetable of its own; its times follow from the trains around it, using the
   * scan's own arithmetic. A link after a leg starts when that leg arrives plus the interchange at
   * the link's origin, and ends a duration later. A link before the first train (or before another
   * link) must end an interchange before that leg departs, and so starts a duration earlier - the
   * latest start that still makes the connection. (The TS reference left these null; BUGS.md 4.2.)
   */
  private static List<Leg> withFixedLinkTimes(List<Untimed> untimed) {
    int firstTrain = 0;
    while (firstTrain < untimed.size() && !(untimed.get(firstTrain) instanceof Train)) {
      firstTrain++;
    }
    // Nothing to anchor on without a train. A link-only journey has no legs, and the caller drops it.
    if (firstTrain == untimed.size()) {
      return List.of();
    }
    Leg[] legs = new Leg[untimed.size()];
    for (int i = firstTrain; i < legs.length; i++) {
      legs[i] = switch (untimed.get(i)) {
        case Train train -> train.leg();
        case Link(TransferLeg link) -> {
          LocalDateTime departure = legs[i - 1].arrivalTime().plusSeconds(link.originInterchange());
          yield fixedLink(link, departure, departure.plusSeconds(link.duration()));
        }
      };
    }
    // Everything before the first train is a link.
    for (int i = firstTrain - 1; i >= 0; i--) {
      TransferLeg link = ((Link) untimed.get(i)).link();
      LocalDateTime arrival = legs[i + 1].departureTime().minusSeconds(link.destinationInterchange());
      legs[i] = fixedLink(link, arrival.minusSeconds(link.duration()), arrival);
    }
    return List.of(legs);
  }

  private static Leg.FixedLink fixedLink(TransferLeg link, LocalDateTime departure, LocalDateTime arrival) {
    return new Leg.FixedLink(link.origin(), link.destination(), departure, arrival, link.duration(),
                             link.originInterchange(), link.destinationInterchange(), link.mode());
  }

  private LocalDateTime toDateTime(int secondsSinceMidnight) {
    return LocalDateTime.of(startDate, LocalTime.MIDNIGHT).plusSeconds(secondsSinceMidnight);
  }

  /** The train as it runs on a day: the query's date, or the next for a train that runs on the next day's timetable. */
  private TrainService toService(TrainRun train) {
    return new TrainService(train.tripId(), startDate.plusDays(train.dayOffset()), train.trainUid(),
                            train.retailServiceId());
  }

  private static RailTrip railTripOf(Trip trip) {
    return (RailTrip) trip;
  }

  // stopTimes is passed in (rather than read from trip) so it reflects any overlay day offset.
  private TrainTrip toTrainTrip(RailTrip trip, List<StopTime> stopTimes) {
    List<StopDateTime> railStopTimes = new ArrayList<>(stopTimes.size());
    for (int i = 0; i < stopTimes.size(); i++) {
      railStopTimes.add(toStopDateTime(stopTimes.get(i), trip.timetabledCall(i), trip.platform(i),
                                       trip.stopHeadsign(i)));
    }

    return new TrainTrip(
            List.copyOf(railStopTimes),
            trip.trains().stream().map(this::toService).toList(),
            trip.headsign(),
            trip.mode(),
            trip.operator(),
            trip.route(),
            trip.associations()
    );
  }

  // The times are the routed call's, which carry any overlay day offset; whether a passenger may board and alight
  // is the timetabled call's, which a through trip restricts for routing only.
  private StopDateTime toStopDateTime(StopTime stopTime, StopTime timetabled, @Nullable String platform,
                                      @Nullable String headsign) {
    return new StopDateTime(
            stopTime.stop(),
            toDateTime(stopTime.departureTime()),
            toDateTime(stopTime.arrivalTime()),
            timetabled.canBoard(),
            timetabled.canAlight(),
            timetabled.pickup(),
            timetabled.dropOff(),
            platform,
            headsign);
  }
}
