package com.joshuaharwood.velociraptor.server;

import com.joshuaharwood.velociraptor.gtfs.ExtendedGtfsRelationalDaoImpl;
import com.joshuaharwood.velociraptor.gtfs.FeedProfile;
import com.joshuaharwood.velociraptor.rail.Operator;
import com.joshuaharwood.velociraptor.rail.RailTrip;
import com.joshuaharwood.velociraptor.rail.Route;
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
 * Builds the {@link RailTrip}s the server plans on, one for each trip a service date runs, carrying the GTFS metadata
 * the raptor core treats as opaque. Shared by every service date's build, so a trip's platforms and who runs it are
 * read from the feed once.
 */
final class RailTrips {

  private final ExtendedGtfsRelationalDaoImpl dao;
  private final FeedProfile profile;
  // A trip's platforms are the same on every service date it runs, so each service date's copy of the trip shares
  // the one list rather than reading the trip's stop times again.
  private final ConcurrentHashMap<AgencyAndId, List<@Nullable String>> platformsByTrip = new ConcurrentHashMap<>();
  // One operator per agency, shared by every trip it runs; one route per route, shared likewise.
  private final ConcurrentHashMap<String, Operator> operatorsByAgency = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<AgencyAndId, Route> routesById = new ConcurrentHashMap<>();

  RailTrips(ExtendedGtfsRelationalDaoImpl dao) {
    this.dao = dao;
    this.profile = dao.feedProfile();
  }

  /**
   * The trip as the feed has it. The ATOC/CIF train UID (e.g. W45490) is wherever the feed's profile says: the lead
   * of a gb-transit trip_id, or the trip_headsign of the deprecated dtd2gtfs feed. The trip_id itself is kept as id().
   */
  Trip trip(org.onebusaway.gtfs.model.Trip obaTrip, List<StopTime> stopTimes) {
    var agency = obaTrip.getRoute().getAgency();
    return new RailTrip(
        obaTrip.getId().getId(),
        stopTimes,
        obaTrip.getServiceId().getId(),
        profile.trainUid(obaTrip),
        retailServiceIdOf(obaTrip),
        profile.headsign(obaTrip),
        TransitMode.fromGtfs(obaTrip.getRoute().getType()),
        operatorsByAgency.computeIfAbsent(agency.getId(), _ -> operatorOf(agency)),
        routesById.computeIfAbsent(obaTrip.getRoute().getId(), _ -> routeOf(obaTrip.getRoute())),
        platformsByTrip.computeIfAbsent(obaTrip.getId(), _ -> platformsOf(obaTrip)));
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
   * The platform of each of the trip's calls, in stop-time order. gb-transit calls at a boarding point under the
   * station, whose {@code platform_code} is the platform ({@code 9100BRGHTN5} is Brighton platform 5); a call at
   * the station itself, or at a stop in a feed with no platforms, has none, and a trip with none at all shares the
   * one empty list.
   */
  private List<@Nullable String> platformsOf(org.onebusaway.gtfs.model.Trip obaTrip) {
    var platforms = new ArrayList<@Nullable String>();
    for (var stopTime : dao.getStopTimesForTrip(obaTrip)) {
      platforms.add(blankToNull(((org.onebusaway.gtfs.model.Stop) stopTime.getStop()).getPlatformCode()));
    }
    return platforms.stream().allMatch(Objects::isNull) ? List.of() : Collections.unmodifiableList(platforms);
  }
}
