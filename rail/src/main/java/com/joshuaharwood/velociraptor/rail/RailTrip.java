package com.joshuaharwood.velociraptor.rail;

import org.jspecify.annotations.Nullable;
import com.joshuaharwood.velociraptor.raptor.model.StopTime;
import com.joshuaharwood.velociraptor.raptor.model.Trip;

import java.util.List;

/**
 * Trip enriched with GTFS metadata needed to produce rail result DTOs.
 *
 * The raptor algorithm treats this as an opaque {@link Trip}; the id and stopTimes are the only fields it reads.
 * The extra fields flow through kConnections and get unpacked by {@link RailJourneyFactory}.
 *
 * @param id            the trip's id: a train's GTFS trip_id, or for a through trip its trains' joined with
 *                      {@code +}, which is not a trip_id and is not shown
 * @param trains        the trains the trip is made of, in order: one, or one more than it has associations
 * @param headsign      where the trip is going, as a passenger reads it; null where the feed gives none
 * @param operator      the company running the trip
 * @param route         the line or brand it runs under
 * @param platforms     the platform of each call, by the same index as {@code stopTimes}, null where the feed names
 *                      none; empty where the feed names no platforms at all
 * @param stopHeadsigns where the train is going as each call shows it, by the same index, null where the call shows
 *                      the trip's own headsign; empty where no call has its own
 * @param associations  where the trip stays aboard from one train onto the next, in call order; empty for a trip that
 *                      is one train throughout
 * @param timetable     a through trip's calls as the trains make them, where they differ from {@code stopTimes},
 *                      which only board before its association and set down after it; empty for any other trip
 */
public record RailTrip(String id,
                       List<StopTime> stopTimes,
                       List<TrainRun> trains,
                       @Nullable String headsign,
                       TransitMode mode,
                       Operator operator,
                       Route route,
                       List<@Nullable String> platforms,
                       List<@Nullable String> stopHeadsigns,
                       List<Association> associations,
                       List<StopTime> timetable) implements Trip {

  public RailTrip {
    if (trains.size() != associations.size() + 1) {
      throw new IllegalArgumentException(
          "trip " + id + " has " + trains.size() + " trains for " + associations.size() + " associations");
    }
  }

  /** The call at {@code stopIndex} as the timetable has it: whether and how a passenger may board and alight. */
  public StopTime timetabledCall(int stopIndex) {
    return timetable.isEmpty() ? stopTimes.get(stopIndex) : timetable.get(stopIndex);
  }

  /** The platform of the call at {@code stopIndex}, or null where the feed names none. */
  public @Nullable String platform(int stopIndex) {
    return stopIndex < platforms.size() ? platforms.get(stopIndex) : null;
  }

  /** Where the train is going as the call at {@code stopIndex} shows it, or null where it shows the trip's own. */
  public @Nullable String stopHeadsign(int stopIndex) {
    return stopIndex < stopHeadsigns.size() ? stopHeadsigns.get(stopIndex) : null;
  }

  /** The train boarded at the call at {@code stopIndex}: at an association, the one that leaves. */
  public TrainRun boardingTrain(int stopIndex) {
    return trainAt(stopIndex, true);
  }

  /** The train alighted from at the call at {@code stopIndex}: at an association, the one that arrived. */
  public TrainRun alightingTrain(int stopIndex) {
    return trainAt(stopIndex, false);
  }

  /** The train that leaves the k-th association. */
  public TrainRun trainAfter(int association) {
    return trains.get(association + 1);
  }

  // Boarding at an association is boarding the train that leaves it, so the association counts as made; alighting
  // there is leaving the one that arrived, so it does not. A dtd2gtfs trip that changed headcode is one train with
  // its two UIDs joined, "W12345_W67890", and no associations: the first is boarded and the last alighted from.
  private TrainRun trainAt(int stopIndex, boolean boarding) {
    int made = 0;
    for (Association association : associations) {
      if (association.stopIndex() < stopIndex || (boarding && association.stopIndex() == stopIndex)) {
        made++;
      }
    }
    TrainRun train = trains.get(made);
    String uid = train.trainUid();
    if (associations.isEmpty() && uid != null && uid.contains("_")) {
      String[] uids = uid.split("_");
      return new TrainRun(train.tripId(), train.dayOffset(), boarding ? uids[0] : uids[uids.length - 1],
                          train.retailServiceId());
    }
    return train;
  }
}
