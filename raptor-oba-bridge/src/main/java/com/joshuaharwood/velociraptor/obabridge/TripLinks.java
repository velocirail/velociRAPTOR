package com.joshuaharwood.velociraptor.obabridge;

import com.joshuaharwood.velociraptor.gtfs.ExtendedGtfsRelationalDaoImpl;
import com.joshuaharwood.velociraptor.raptor.model.PickupDropOffType;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.model.StopTime;
import com.joshuaharwood.velociraptor.raptor.model.Trip;
import org.jspecify.annotations.Nullable;
import org.onebusaway.gtfs.model.AgencyAndId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Follows a feed's in-seat transfers ({@code transfer_type} 4) - the divides, joins and onward workings a passenger
 * stays aboard across - into through trips for {@link RaptorAlgorithmFactory} to plan on beside the trains themselves.
 */
final class TripLinks {

  /** GTFS {@code transfer_type} 4: the passenger stays aboard from one trip onto the next. */
  private static final int IN_SEAT = 4;

  private static final int SECONDS_PER_DAY = 24 * 60 * 60;

  private TripLinks() {
  }

  /**
   * The through trips for the day's in-seat transfers ({@code transfer_type} 4). Where a train divides, joins
   * another or runs on under a new identity, gb-transit keeps each part as a trip of its own and links them; a
   * passenger staying aboard rides the two as one, with no change and no interchange. A link is followed only where
   * both trips run that day and the train is still there to stay aboard on - {@code to} leaves no earlier than
   * {@code from} arrives. One whose trips are both already running and both carry on is neither a divide, a join
   * nor an onward working, and is left alone.
   * <p>
   * The link names a stop for each trip and the two can differ (a train forming another at a different platform),
   * so each trip's call is found by station.
   * <p>
   * A train that reaches the link after midnight is already on the following service day's timetable, and so may be
   * what it divides into, joins or forms: the northbound Caledonian Sleeper reaches Edinburgh at 04:00, as 28:00 on
   * its own service day, and its Aberdeen and Fort William portions leave at 04:28 on the next. Where {@code to}
   * does not leave that day after {@code from} arrives, its run on the following day is followed instead, a day
   * later on this day's time-line. Only after midnight: a train that simply misses its onward train is not linked to
   * tomorrow's.
   *
   * @param nextDay a trip as it runs on the following service day, or null where it does not
   */
  static List<Trip> follow(ExtendedGtfsRelationalDaoImpl dao,
                           Map<AgencyAndId, Trip> trips,
                           Function<org.onebusaway.gtfs.model.Trip, @Nullable Trip> nextDay,
                           Function<org.onebusaway.gtfs.model.Stop, Stop> toStop,
                           TripLinker tripLinker) {
    // shift: what to add to the times of to, run on the following day, to put it on this day's time-line
    record Candidate(Trip from, Trip to, int fromIndex, int toIndex, int shift) {
      Stop station() {
        return from.stopTimes().get(fromIndex).stop();
      }
    }

    // One trip per onward train run the following day, so the links into it count it once.
    final Map<AgencyAndId, Trip> nextDayTrips = new HashMap<>();
    final List<Candidate> candidates = new ArrayList<>();
    for (org.onebusaway.gtfs.model.Transfer transfer : dao.getAllTransfers()) {
      if (transfer.getTransferType() != IN_SEAT || transfer.getFromTrip() == null || transfer.getToTrip() == null) {
        continue;
      }
      final Trip from = trips.get(transfer.getFromTrip().getId());
      final int fromIndex = from == null ? -1
                          : indexOf(from, toStop.apply((org.onebusaway.gtfs.model.Stop) transfer.getFromStop()));
      if (from == null || fromIndex < 0) {
        continue;
      }
      final int arrival = from.arrivalTime(fromIndex);
      final Stop toStation = toStop.apply((org.onebusaway.gtfs.model.Stop) transfer.getToStop());

      Trip to = trips.get(transfer.getToTrip().getId());
      int toIndex = to == null ? -1 : indexOf(to, toStation);
      int shift = 0;
      if (to == null || toIndex < 0 || to.departureTime(toIndex) < arrival) {
        if (arrival < SECONDS_PER_DAY) {
          continue;
        }
        var obaTo = transfer.getToTrip();
        to = nextDayTrips.computeIfAbsent(obaTo.getId(), _ -> nextDay.apply(obaTo));
        toIndex = to == null ? -1 : indexOf(to, toStation);
        shift = SECONDS_PER_DAY;
        if (to == null || toIndex < 0 || to.departureTime(toIndex) + shift < arrival) {
          continue;
        }
      }
      candidates.add(new Candidate(from, to, fromIndex, toIndex, shift));
    }

    // Two trips starting where one ends is a divide, and two ending where one starts a join, so how a link reads
    // depends on the other links at the same station.
    final Map<Trip, Map<Stop, List<Trip>>> leavingFrom = new HashMap<>();
    final Map<Trip, Map<Stop, List<Trip>>> joiningTo = new HashMap<>();
    for (Candidate c : candidates) {
      leavingFrom.computeIfAbsent(c.from(), _ -> new HashMap<>())
                 .computeIfAbsent(c.station(), _ -> new ArrayList<>()).add(c.to());
      joiningTo.computeIfAbsent(c.to(), _ -> new HashMap<>())
               .computeIfAbsent(c.station(), _ -> new ArrayList<>()).add(c.from());
    }

    final List<Trip> linked = new ArrayList<>(candidates.size());
    for (Candidate c : candidates) {
      final boolean fromCarriesOn = c.fromIndex() < c.from().stopTimes().size() - 1;
      final boolean toAlreadyRunning = c.toIndex() > 0;
      final List<Trip> leaving = leavingFrom.get(c.from()).get(c.station());
      final int joining = joiningTo.get(c.to()).get(c.station()).size();

      final TripLink.Type type;
      final List<Trip> otherPortions;
      if (fromCarriesOn && toAlreadyRunning) {
        continue;
      } else if (fromCarriesOn) {
        type = TripLink.Type.DIVIDE;
        // from carries on itself, and any other portion leaving it here goes elsewhere too: the northbound sleeper
        // runs on to Inverness and leaves portions for Aberdeen and Fort William.
        otherPortions = new ArrayList<>(List.of(c.from()));
        leaving.stream().filter(t -> t != c.to()).forEach(otherPortions::add);
      } else if (toAlreadyRunning || (joining > 1 && leaving.size() == 1)) {
        type = TripLink.Type.JOIN;
        otherPortions = List.of();
      } else if (leaving.size() > 1 && joining == 1) {
        type = TripLink.Type.DIVIDE;
        otherPortions = leaving.stream().filter(t -> t != c.to()).toList();
      } else {
        type = TripLink.Type.NEXT;
        otherPortions = List.of();
      }

      final List<StopTime> fromCalls = c.from().stopTimes();
      final List<StopTime> toCalls = shifted(c.to().stopTimes(), c.shift());
      final StopTime arriving = fromCalls.get(c.fromIndex());
      final StopTime departing = toCalls.get(c.toIndex());
      final List<StopTime> timetable = new ArrayList<>(c.fromIndex() + toCalls.size() - c.toIndex());
      timetable.addAll(fromCalls.subList(0, c.fromIndex()));
      timetable.add(new StopTime(arriving.stop(), arriving.arrivalTime(), departing.departureTime(),
                                 departing.pickup(), arriving.dropOff()));
      timetable.addAll(toCalls.subList(c.toIndex() + 1, toCalls.size()));

      linked.add(tripLinker.link(new TripLink(c.from(), c.to(), c.fromIndex(), c.toIndex(),
                                              c.shift() / SECONDS_PER_DAY, type, otherPortions),
                                 staysAboardAcross(timetable, c.fromIndex()),
                                 Collections.unmodifiableList(timetable)));
    }
    return linked;
  }

  /**
   * The through trip's calls as it is routed on: boarded only before the link at {@code at} and left only after it.
   * Up to the link it runs at the same times as {@code from}, and after it as {@code to}, so were it boarded or left
   * on either side it would tie with that train for every journey that does not cross the link, and could be
   * returned for one in its place - under the wrong train's ids and destination. Restricted, it carries only the
   * journeys that stay aboard, and its pattern of pick-ups and set-downs puts it on a route of its own.
   */
  private static List<StopTime> staysAboardAcross(List<StopTime> timetable, int at) {
    final List<StopTime> stopTimes = new ArrayList<>(timetable.size());
    for (int i = 0; i < timetable.size(); i++) {
      final StopTime call = timetable.get(i);
      stopTimes.add(new StopTime(call.stop(), call.arrivalTime(), call.departureTime(),
                                 i < at ? call.pickup() : PickupDropOffType.NONE,
                                 i > at ? call.dropOff() : PickupDropOffType.NONE));
    }
    return Collections.unmodifiableList(stopTimes);
  }

  /** The calls a {@code shift} of seconds later. */
  private static List<StopTime> shifted(List<StopTime> calls, int shift) {
    if (shift == 0) {
      return calls;
    }
    final List<StopTime> later = new ArrayList<>(calls.size());
    for (StopTime call : calls) {
      later.add(new StopTime(call.stop(), call.arrivalTime() + shift, call.departureTime() + shift, call.pickup(),
                             call.dropOff()));
    }
    return later;
  }

  /** The first call of {@code trip} at {@code station}, or -1. */
  private static int indexOf(Trip trip, Stop station) {
    final List<StopTime> stopTimes = trip.stopTimes();
    for (int i = 0; i < stopTimes.size(); i++) {
      if (stopTimes.get(i).stop().equals(station)) {
        return i;
      }
    }
    return -1;
  }
}
