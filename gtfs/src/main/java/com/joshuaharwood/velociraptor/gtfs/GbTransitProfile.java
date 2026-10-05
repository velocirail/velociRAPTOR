package com.joshuaharwood.velociraptor.gtfs;

import org.jspecify.annotations.Nullable;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.Trip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The gb-transit shape. {@code stop_times.txt} names boarding points ({@code 9100BRGHTN5}) under a station
 * ({@code 910GBRGHTN}), and a stop routes under its station's {@code stop_code}: the CRS code for a rail station,
 * a three character code starting with a digit for a TfL station of its own. A TfL platform inside a rail station
 * is one of that station's children, so it routes as the rail station and changes there on its interchange time.
 * <p>
 * The train UID is the first part of a CIF {@code trip_id} ({@code C00049_20260517_20261206}). Anything else - a
 * {@code tfl_} trip - has none.
 */
final class GbTransitProfile implements FeedProfile {

  private static final Logger LOGGER = LoggerFactory.getLogger(GbTransitProfile.class);

  /** UID, schedule start, schedule end, and the {@code _2} gb-transit adds where two schedules would collide. */
  private static final Pattern CIF_TRIP_ID = Pattern.compile("([A-Z][A-Z0-9]{5})_\\d{8}_\\d{8}(?:_\\d+)?");

  private final Map<AgencyAndId, String> keys;

  private GbTransitProfile(Map<AgencyAndId, String> keys) {
    this.keys = keys;
  }

  static GbTransitProfile of(Collection<Stop> stops) {
    Map<AgencyAndId, Stop> byId = new HashMap<>(stops.size());
    for (Stop stop : stops) {
      byId.put(stop.getId(), stop);
    }

    Map<AgencyAndId, String> keys = new HashMap<>(stops.size());
    int withoutCode = 0;
    for (Stop stop : stops) {
      Stop station = stop;
      String parent = stop.getParentStation();
      if (parent != null && !parent.isEmpty()) {
        Stop found = byId.get(new AgencyAndId(stop.getId().getAgencyId(), parent));
        if (found == null) {
          LOGGER.warn("Stop {} names parent station {}, which is not in the feed; routing it as itself", stop.getId(), parent);
        } else {
          station = found;
        }
      }
      String code = station.getCode();
      if (code == null || code.isBlank()) {
        withoutCode++;
        keys.put(stop.getId(), station.getId().getId());
      } else {
        keys.put(stop.getId(), code);
      }
    }
    if (withoutCode > 0) {
      LOGGER.warn("{} stops have no stop_code on their station and route under the station's stop_id", withoutCode);
    }
    return new GbTransitProfile(keys);
  }

  @Override
  public String stopKey(Stop stop) {
    String key = keys.get(stop.getId());
    return key != null ? key : stop.getId().getId();
  }

  @Override
  public @Nullable String trainUid(Trip trip) {
    var matcher = CIF_TRIP_ID.matcher(trip.getId().getId());
    return matcher.matches() ? matcher.group(1) : null;
  }
}
