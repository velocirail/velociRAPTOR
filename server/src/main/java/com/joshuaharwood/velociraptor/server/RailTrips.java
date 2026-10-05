package com.joshuaharwood.velociraptor.server;

import com.joshuaharwood.velociraptor.gtfs.ExtendedGtfsRelationalDaoImpl;
import com.joshuaharwood.velociraptor.gtfs.FeedProfile;
import com.joshuaharwood.velociraptor.obabridge.TripLink;
import com.joshuaharwood.velociraptor.obabridge.TripLinker;
import com.joshuaharwood.velociraptor.rail.Association;
import com.joshuaharwood.velociraptor.rail.AssociationType;
import com.joshuaharwood.velociraptor.rail.Operator;
import com.joshuaharwood.velociraptor.rail.RailTrip;
import com.joshuaharwood.velociraptor.rail.Route;
import com.joshuaharwood.velociraptor.rail.TrainRun;
import com.joshuaharwood.velociraptor.rail.TransitMode;
import com.joshuaharwood.velociraptor.raptor.model.StopTime;
import com.joshuaharwood.velociraptor.raptor.model.Trip;
import org.jspecify.annotations.Nullable;
import org.onebusaway.gtfs.model.Agency;
import org.onebusaway.gtfs.model.AgencyAndId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static com.joshuaharwood.velociraptor.server.FeedValues.blankToNull;

/**
 * Builds the {@link RailTrip}s the server plans on, carrying the GTFS metadata the raptor core treats as opaque: one
 * for each trip a service date runs, and one for each divide, join or onward working a passenger stays aboard across.
 * Shared by every service date's build, so what a trip's calls show and who runs it are read from the feed once.
 */
final class RailTrips implements TripLinker {

  private final ExtendedGtfsRelationalDaoImpl dao;
  private final FeedProfile profile;
  // A trip's platforms and stop headsigns are the same on every service date it runs, so each service date's copy
  // of the trip shares the one pair of lists rather than reading the trip's stop times again.
  private final ConcurrentHashMap<AgencyAndId, Calls> callsByTrip = new ConcurrentHashMap<>();
  // One operator per agency, shared by every trip it runs; one route per route, shared likewise.
  private final ConcurrentHashMap<String, Operator> operatorsByAgency = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<AgencyAndId, Route> routesById = new ConcurrentHashMap<>();

  RailTrips(ExtendedGtfsRelationalDaoImpl dao) {
    this.dao = dao;
    this.profile = dao.feedProfile();
  }

  /**
   * The trip as the feed has it: one train. The ATOC/CIF train UID (e.g. W45490) is wherever the feed's profile
   * says: the lead of a gb-transit trip_id, or the trip_headsign of the deprecated dtd2gtfs feed.
   */
  Trip trip(org.onebusaway.gtfs.model.Trip obaTrip, List<StopTime> stopTimes) {
    var calls = callsByTrip.computeIfAbsent(obaTrip.getId(), _ -> callsOf(obaTrip));
    var agency = obaTrip.getRoute().getAgency();
    return new RailTrip(
        obaTrip.getId().getId(),
        stopTimes,
        List.of(new TrainRun(obaTrip.getId().getId(), 0, profile.trainUid(obaTrip), retailServiceIdOf(obaTrip))),
        profile.headsign(obaTrip),
        TransitMode.fromGtfs(obaTrip.getRoute().getType()),
        operatorsByAgency.computeIfAbsent(agency.getId(), _ -> operatorOf(agency)),
        routesById.computeIfAbsent(obaTrip.getRoute().getId(), _ -> routeOf(obaTrip.getRoute())),
        calls.platforms(),
        calls.headsigns(),
        List.of(),
        List.of());
  }

  /**
   * The trip a passenger rides by staying aboard from {@code from} onto {@code to}: both trains, the calls of each
   * either side of the link, and the association that says what happens there. Its operator and mode are the train
   * boarded's, and its headsign the onward train's, since every journey that rides it boards {@code from} and
   * alights from {@code to} (see {@link TripLinker}).
   */
  @Override
  public Trip link(TripLink link, List<StopTime> stopTimes, List<StopTime> timetable) {
    var from = (RailTrip) link.from();
    var to = (RailTrip) link.to();
    int at = link.fromIndex();
    int offset = link.toIndex() - at;

    var platforms = new ArrayList<@Nullable String>(stopTimes.size());
    var headsigns = new ArrayList<@Nullable String>(stopTimes.size());
    for (int i = 0; i < stopTimes.size(); i++) {
      // At the link itself the train arrives as from does, at its platform, and leaves showing where to goes.
      // Before it, each call shows what from's does - its own headsign, or else from's destination - since the
      // through trip's own headsign is where it ends up.
      platforms.add(i <= at ? from.platform(i) : to.platform(i + offset));
      headsigns.add(i >= at ? to.stopHeadsign(i + offset)
                  : from.stopHeadsign(i) != null ? from.stopHeadsign(i) : from.headsign());
    }

    var type = switch (link.type()) {
      case DIVIDE -> AssociationType.DIVIDE;
      case JOIN -> AssociationType.JOIN;
      case NEXT -> AssociationType.NEXT;
    };
    var otherHeadsigns = link.otherPortions().stream()
                             .map(portion -> ((RailTrip) portion).headsign())
                             .filter(Objects::nonNull)
                             .toList();
    var associations = new ArrayList<>(from.associations());
    associations.add(new Association(at, type, to.headsign(), otherHeadsigns));

    // to may run on the following service day, a sleeper's portion leaving after midnight.
    var trains = new ArrayList<>(from.trains());
    to.trains().forEach(train -> trains.add(new TrainRun(train.tripId(), train.dayOffset() + link.dayOffset(),
                                                         train.trainUid(), train.retailServiceId())));

    return new RailTrip(from.id() + "+" + to.id(), stopTimes, List.copyOf(trains), to.headsign(), from.mode(),
                        from.operator(), from.route(), orEmpty(platforms), orEmpty(headsigns),
                        List.copyOf(associations), timetable);
  }

  /**
   * The operator's code. gb-transit publishes a rail operator's ATOC code in National Operator Code form, with an
   * {@code =} prefix ({@code =GW}), and TfL's operators by their own ({@code LUL}); the deprecated dtd2gtfs feed's
   * agency_id is the ATOC code itself ({@code GW}).
   */
  static String operatorOf(String agencyId) {
    return agencyId.startsWith("=") ? agencyId.substring(1) : agencyId;
  }

  /** The operator an agency describes, with its code in ATOC form. */
  private static Operator operatorOf(Agency agency) {
    return new Operator(operatorOf(agency.getId()), agency.getId(), blankToNull(agency.getName()),
                        blankToNull(agency.getUrl()), blankToNull(agency.getPhone()));
  }

  /** The line a route describes. */
  private static Route routeOf(org.onebusaway.gtfs.model.Route route) {
    return new Route(route.getId().getId(), blankToNull(route.getShortName()), blankToNull(route.getLongName()),
                     blankToNull(route.getColor()), blankToNull(route.getTextColor()), blankToNull(route.getUrl()));
  }

  /**
   * The trip's retail service ID, its {@code trip_short_name}: the two-letter operator code and six digits
   * ({@code SN430003}) that National Rail's retail systems know the train by.
   */
  private static @Nullable String retailServiceIdOf(org.onebusaway.gtfs.model.Trip obaTrip) {
    return blankToNull(obaTrip.getTripShortName());
  }

  /**
   * What a trip's calls show, in stop-time order: the platform and the headsign.
   *
   * @param platforms by call; empty where no call names one
   * @param headsigns by call, the call's own {@code stop_headsign}; empty where no call has one
   */
  private record Calls(List<@Nullable String> platforms, List<@Nullable String> headsigns) {
  }

  /**
   * The platform and headsign of each of the trip's calls. gb-transit calls at a boarding point under the station,
   * whose {@code platform_code} is the platform ({@code 9100BRGHTN5} is Brighton platform 5); a call at the station
   * itself, or at a stop in a feed with no platforms, has none. A call's {@code stop_headsign} is where a train that
   * divides further on goes ({@code Portsmouth Harbour and Bognor Regis}); most calls have none, so a trip with none
   * shares the one empty list.
   */
  private Calls callsOf(org.onebusaway.gtfs.model.Trip obaTrip) {
    var platforms = new ArrayList<@Nullable String>();
    var headsigns = new ArrayList<@Nullable String>();
    for (var stopTime : dao.getStopTimesForTrip(obaTrip)) {
      platforms.add(blankToNull(((org.onebusaway.gtfs.model.Stop) stopTime.getStop()).getPlatformCode()));
      headsigns.add(blankToNull(stopTime.getStopHeadsign()));
    }
    return new Calls(orEmpty(platforms), orEmpty(headsigns));
  }

  private static List<@Nullable String> orEmpty(List<@Nullable String> values) {
    return values.stream().allMatch(Objects::isNull) ? List.of() : Collections.unmodifiableList(values);
  }
}
