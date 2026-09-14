package com.joshuaharwood.velociraptor.raptor;

import com.joshuaharwood.velociraptor.raptor.model.DefaultTrip;
import com.joshuaharwood.velociraptor.raptor.model.Leg;
import com.joshuaharwood.velociraptor.raptor.model.Leg.TimetableLeg;
import com.joshuaharwood.velociraptor.raptor.model.Leg.TransferLeg;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.model.StopTime;
import com.joshuaharwood.velociraptor.raptor.model.Trip;
import com.joshuaharwood.velociraptor.raptor.result.Journey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test utility methods mirroring the TS test util.ts
 */
public final class TestData {

  private static int tripCounter = 0;

  private TestData() {}

  /** Cache of Stop objects by ID so the same string always returns the same Stop instance */
  private static final Map<String, Stop> STOP_CACHE = new HashMap<>();

  public static Stop stop(String id) {
    return STOP_CACHE.computeIfAbsent(id, Stop::new);
  }

  /**
   * Create a StopTime. Mirrors TS st(stop, arrivalTime, departureTime).
   *
   * null arrivalTime means no drop off (arrivalTime set to departureTime, canAlight=false).
   * null departureTime means no pick up (departureTime set to arrivalTime, canBoard=false).
   */
  public static StopTime st(String stopId, Integer arrivalTime, Integer departureTime) {
    int resolvedArrival = arrivalTime != null ? arrivalTime : departureTime;
    int resolvedDeparture = departureTime != null ? departureTime : arrivalTime;
    boolean canAlight = arrivalTime != null;
    boolean canBoard = departureTime != null;
    return new StopTime(stop(stopId), resolvedArrival, resolvedDeparture, canBoard, canAlight);
  }

  /**
   * Create a StopTime with explicit board/alight control. Mirrors TS via().
   */
  public static StopTime via(String stopId, Integer arrivalTime, Integer departureTime,
                      boolean canAlight, boolean canBoard) {
    int resolvedArrival = arrivalTime != null ? arrivalTime : departureTime;
    int resolvedDeparture = departureTime != null ? departureTime : arrivalTime;
    return new StopTime(stop(stopId), resolvedArrival, resolvedDeparture, canBoard, canAlight);
  }

  /**
   * Create a Trip from stop times. Mirrors TS t(...stopTimes).
   */
  public static Trip t(StopTime... stopTimes) {
    return new DefaultTrip("trip" + tripCounter++, List.of(stopTimes));
  }

  /**
   * Create a transfer leg. Mirrors TS tf(origin, destination, duration).
   */
  public static TransferLeg tf(String origin, String destination, int duration) {
    return new TransferLeg(stop(origin), stop(destination), duration, 0, Integer.MAX_VALUE);
  }

  /**
   * Create a transfer leg with interchange times. Mirrors TS tfi().
   */
  public static TransferLeg tfi(String origin, String destination, int duration,
                         int originInterchange, int destinationInterchange) {
    return new TransferLeg(stop(origin), stop(destination), duration, 0, Integer.MAX_VALUE,
      originInterchange, destinationInterchange, null);
  }

  /**
   * Build a Journey from legs. Each argument is either a List&lt;StopTime&gt; (timetable leg)
   * or a TransferLeg. Mirrors TS j(...legStopTimes).
   */
  public static Journey j(Object... legParts) {
    List<Leg> legs = new ArrayList<>();

    for (Object part : legParts) {
      if (part instanceof TransferLeg transfer) {
        legs.add(transfer);
      } else if (part instanceof List<?> stopTimes) {
        @SuppressWarnings("unchecked")
        var stList = (List<StopTime>) stopTimes;
        var origin = stList.getFirst().stop();
        var destination = stList.getLast().stop();
        legs.add(new TimetableLeg(origin, destination, stList, null));
      }
    }

    int departureTime = getDepartureTime(legs);
    int arrivalTime = getArrivalTime(legs);

    return new Journey(legs, departureTime, arrivalTime);
  }

  private static int getDepartureTime(List<Leg> legs) {
    int transferDuration = 0;
    for (Leg leg : legs) {
      switch (leg) {
        case TimetableLeg tl -> { return tl.stopTimes().getFirst().departureTime() - transferDuration; }
        // Mirror JourneyFactory: a leading fixed link's board (destination) interchange backs off departure.
        case TransferLeg tr -> transferDuration += tr.duration() + tr.destinationInterchange();
      }
    }
    return 0;
  }

  private static int getArrivalTime(List<Leg> legs) {
    int transferDuration = 0;
    for (int i = legs.size() - 1; i >= 0; i--) {
      switch (legs.get(i)) {
        case TimetableLeg tl -> { return tl.stopTimes().getLast().arrivalTime() + transferDuration; }
        // Mirror JourneyFactory: a trailing fixed link's entry interchange counts toward arrival.
        case TransferLeg tr -> transferDuration += tr.duration() + tr.originInterchange();
      }
    }
    return 0;
  }

  /**
   * Strip trip references from Journey results for comparison.
   * Mirrors TS setDefaultTrip().
   */
  public static List<Journey> stripTrips(List<Journey> journeys) {
    return journeys.stream()
      .map(journey -> new Journey(
        journey.legs().stream().map(leg -> switch (leg) {
          case TimetableLeg tl -> (Leg) new TimetableLeg(tl.origin(), tl.destination(), tl.stopTimes(), null);
          case TransferLeg tr -> tr;
        }).toList(),
        journey.departureTime(),
        journey.arrivalTime()))
      .toList();
  }
}
