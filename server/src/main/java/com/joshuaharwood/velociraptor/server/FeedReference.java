package com.joshuaharwood.velociraptor.server;

import com.joshuaharwood.velociraptor.gtfs.ExtendedGtfsRelationalDaoImpl;
import com.joshuaharwood.velociraptor.gtfs.FeedFormat;
import com.joshuaharwood.velociraptor.gtfs.FeedProfile;
import com.joshuaharwood.velociraptor.server.http.dto.ServerInfo;
import com.joshuaharwood.velociraptor.server.http.dto.Station;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jspecify.annotations.Nullable;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.calendar.ServiceDate;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.joshuaharwood.velociraptor.server.FeedValues.blankToNull;
import static com.joshuaharwood.velociraptor.server.VelociraptorConfig.GTFS_SOURCE_FORMAT;

/**
 * What the loaded feed says about itself and its stations, for the reference endpoints. Read from the feed once, as
 * the bean is created - neither changes while the server runs - so concurrent first requests do not each scan it.
 */
@ApplicationScoped
public class FeedReference {

  private final List<Station> stations;
  private final ServerInfo info;

  @Inject
  public FeedReference(ExtendedGtfsRelationalDaoImpl dao,
                       @ConfigProperty(name = "quarkus.application.version") String version,
                       @ConfigProperty(name = GTFS_SOURCE_FORMAT) String format) {
    this.stations = stationsOf(dao);
    this.info = infoOf(dao, version, FeedFormat.fromConfigValue(format));
  }

  /** Every station, by code. */
  public List<Station> stations() {
    return stations;
  }

  public ServerInfo info() {
    return info;
  }

  private static ServerInfo infoOf(ExtendedGtfsRelationalDaoImpl dao, String version, FeedFormat format) {
    var feedInfo = dao.getAllFeedInfos().stream().findFirst().orElse(null);
    return feedInfo == null
        ? new ServerInfo(version, format.configValue(), null, null, null, null, null)
        : new ServerInfo(version, format.configValue(), blankToNull(feedInfo.getVersion()),
                         toLocalDate(feedInfo.getStartDate()), toLocalDate(feedInfo.getEndDate()),
                         blankToNull(feedInfo.getPublisherName()), blankToNull(feedInfo.getPublisherUrl()));
  }

  /**
   * One station per code a journey can be asked for, which is the code its stops route under. In a gb-transit feed
   * that is the station ({@code location_type} 1) whose platforms are its children, and the station's own record
   * describes it; in a dtd2gtfs feed every stop is a station of its own.
   * Package-private so it can be unit-tested without standing up the Quarkus/S3 stack.
   */
  static List<Station> stationsOf(ExtendedGtfsRelationalDaoImpl dao) {
    FeedProfile profile = dao.feedProfile();
    Map<String, Stop> byCode = new LinkedHashMap<>();
    for (Stop stop : dao.getAllStops()) {
      byCode.merge(profile.stopKey(stop), stop,
                   (kept, other) -> other.getLocationType() == Stop.LOCATION_TYPE_STATION ? other : kept);
    }
    return byCode.entrySet().stream()
                 .map(entry -> toStation(entry.getKey(), entry.getValue()))
                 .sorted(Comparator.comparing(Station::code))
                 .toList();
  }

  private static Station toStation(String code, Stop stop) {
    // GTFS wheelchair_boarding: 1 accessible, 2 not, 0 or empty unknown.
    Boolean stepFree = switch (stop.getWheelchairBoarding()) {
      case 1 -> true;
      case 2 -> false;
      default -> null;
    };
    return new Station(code, stop.getName(), stop.getLat(), stop.getLon(), blankToNull(stop.getUrl()), stepFree);
  }

  private static @Nullable LocalDate toLocalDate(@Nullable ServiceDate date) {
    return date == null ? null : LocalDate.of(date.getYear(), date.getMonth(), date.getDay());
  }
}
