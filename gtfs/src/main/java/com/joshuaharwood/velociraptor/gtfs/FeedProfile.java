package com.joshuaharwood.velociraptor.gtfs;

import org.jspecify.annotations.Nullable;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.Trip;

/**
 * How a feed's identifiers map onto the routing model, so the bridge and the rail rendering read every
 * {@link FeedFormat} the same way.
 */
public interface FeedProfile {

  /**
   * The id a stop routes under. Every stop with the same key is one stop to the algorithm, which is how the
   * boarding points of a gb-transit station become the station.
   */
  String stopKey(Stop stop);

  /** The ATOC/CIF train UID of a trip, or null where the trip has none (a TfL trip, say). */
  @Nullable String trainUid(Trip trip);
}
