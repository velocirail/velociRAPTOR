package com.joshuaharwood.velociraptor.gtfs;

import org.jspecify.annotations.Nullable;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.Trip;

/**
 * The dtd2gtfs shape: {@code stop_id} is already the CRS code and the train UID rides in {@code trip_headsign}
 * (two UIDs joined by {@code _} where an association changed headcode). Also how a DAO populated by hand reads.
 */
final class Dtd2GtfsProfile implements FeedProfile {

  static final Dtd2GtfsProfile INSTANCE = new Dtd2GtfsProfile();

  private Dtd2GtfsProfile() {
  }

  @Override
  public String stopKey(Stop stop) {
    return stop.getId().getId();
  }

  @Override
  public @Nullable String trainUid(Trip trip) {
    return trip.getTripHeadsign();
  }
}
