package com.joshuaharwood.velociraptor.gtfs;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The shape of GTFS feed being read. There is no default: a feed is read one way or the other, and reading it the
 * wrong way does not fail loudly on its own - it routes over the wrong stops - so the caller has to say which.
 */
public enum FeedFormat {
  /**
   * A feed published by <a href="https://github.com/planarnetwork/gb-transit">planarnetwork/gb-transit</a>:
   * {@code gtfs.zip}, {@code gtfs-national-rail-only.zip} or {@code gtfs-rail-and-tfl.zip}. Stops are NaPTAN ATCO
   * codes with calls at boarding points under a station, and routing is by the station's {@code stop_code}; fixed
   * links are rows between two stations in {@code transfers.txt}; the train UID leads the {@code trip_id}.
   */
  GB_TRANSIT("gb-transit"),

  /**
   * The feed dtd2mysql wrote before gb-transit: {@code stop_id} is the CRS code, the train UID is in
   * {@code trip_headsign} and fixed links are this project's {@code links.txt}.
   *
   * @deprecated gb-transit publishes the same timetable nightly; read that with {@link #GB_TRANSIT}. This format
   * will be removed in a future release.
   */
  @Deprecated(since = "0.3.0", forRemoval = true)
  DTD2GTFS("dtd2gtfs");

  private final String configValue;

  FeedFormat(String configValue) {
    this.configValue = configValue;
  }

  /** The name the format is configured by, e.g. {@code gb-transit}. */
  public String configValue() {
    return configValue;
  }

  /** @throws IllegalArgumentException naming the accepted values, if {@code value} is not one of them */
  public static FeedFormat fromConfigValue(String value) {
    for (FeedFormat format : values()) {
      if (format.configValue.equalsIgnoreCase(value.strip())) {
        return format;
      }
    }
    throw new IllegalArgumentException("Unknown GTFS feed format '" + value + "'. Expected one of: "
      + Arrays.stream(values()).map(FeedFormat::configValue).collect(Collectors.joining(", ")));
  }

  @Override
  public String toString() {
    return configValue;
  }
}
