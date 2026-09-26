package com.joshuaharwood.velociraptor.gtfs;

import org.jspecify.annotations.Nullable;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The fixed links in a gb-transit {@code transfers.txt}. The file carries three kinds of row, told apart by which
 * columns are set:
 * <ul>
 *   <li>interchange within a station: the same station both ends, no trips - the minimum interchange, not a link;
 *   <li>a fixed link: two stations, no trips, {@code transfer_type} 2 - the rows taken here;
 *   <li>a split or join: the same boarding point, both trips, {@code transfer_type} 4 - not routed on.
 * </ul>
 * A link row carries gb-transit's extension columns for its mode, window, dates and days. Where it leaves them
 * empty - the walks the rail and TfL feed adds between stations - the link is there all day, every day.
 * <p>
 * gb-transit publishes one row per pair of stations, the envelope of the DTD's records for it: the shortest time,
 * the earliest start to the latest end and every day any of them ran. A pair whose records differed by day or time
 * is therefore offered more widely than the DTD said (BUGS.md 10.1).
 */
final class GbTransitFixedLinks {

  private static final Logger LOGGER = LoggerFactory.getLogger(GbTransitFixedLinks.class);

  static final String TRANSFER_TYPE_MIN_TIME = "2";
  static final String UNKNOWN_MODE = "UNKNOWN_MODE";
  static final int START_OF_DAY = 0;
  static final int END_OF_DAY = 24 * 60 * 60 - 1;
  static final ServiceDate EARLIEST = new ServiceDate(1900, 1, 1);
  static final ServiceDate LATEST = new ServiceDate(9999, 12, 31);

  private GbTransitFixedLinks() {
  }

  static List<FixedLink> from(Collection<GbTransitTransfer> rows, FeedProfile profile) {
    var links = new ArrayList<FixedLink>();
    int unusable = 0;
    for (GbTransitTransfer row : rows) {
      if (!isLink(row, profile)) {
        continue;
      }
      Integer duration = parseInt(row.getMinTransferTime());
      if (duration == null) {
        unusable++;
        LOGGER.warn("Fixed link {} has no min_transfer_time; left out", row);
        continue;
      }
      FixedLink link = toFixedLink(row, duration);
      link.setId(links.size() + 1);
      links.add(link);
    }
    if (unusable > 0) {
      LOGGER.warn("{} fixed links in transfers.txt could not be read", unusable);
    }
    return links;
  }

  private static boolean isLink(GbTransitTransfer row, FeedProfile profile) {
    return row.getFromStop() != null
      && row.getToStop() != null
      && isBlank(row.getFromTripId())
      && isBlank(row.getToTripId())
      && TRANSFER_TYPE_MIN_TIME.equals(strip(row.getTransferType()))
      && !profile.stopKey(row.getFromStop()).equals(profile.stopKey(row.getToStop()));
  }

  private static FixedLink toFixedLink(GbTransitTransfer row, int duration) {
    var link = new FixedLink();
    link.setFromStop(row.getFromStop());
    link.setToStop(row.getToStop());
    link.setDurationInSeconds(duration);
    link.setMode(isBlank(row.getMode()) ? UNKNOWN_MODE : row.getMode().strip());
    link.setStartTime(isBlank(row.getStartTime()) ? START_OF_DAY : parseTime(row.getStartTime()));
    link.setEndTime(isBlank(row.getEndTime()) ? END_OF_DAY : parseTime(row.getEndTime()));
    link.setStartDate(isBlank(row.getStartDate()) ? EARLIEST : parseDate(row.getStartDate()));
    link.setEndDate(isBlank(row.getEndDate()) ? LATEST : parseDate(row.getEndDate()));

    String[] days = {row.getMonday(), row.getTuesday(), row.getWednesday(), row.getThursday(), row.getFriday(),
      row.getSaturday(), row.getSunday()};
    boolean everyDay = true;
    for (String day : days) {
      everyDay &= isBlank(day);
    }
    link.setMonday(everyDay || "1".equals(strip(days[0])));
    link.setTuesday(everyDay || "1".equals(strip(days[1])));
    link.setWednesday(everyDay || "1".equals(strip(days[2])));
    link.setThursday(everyDay || "1".equals(strip(days[3])));
    link.setFriday(everyDay || "1".equals(strip(days[4])));
    link.setSaturday(everyDay || "1".equals(strip(days[5])));
    link.setSunday(everyDay || "1".equals(strip(days[6])));
    return link;
  }

  /** {@code H:MM:SS}, hours past 23 allowed as GTFS times are. */
  static int parseTime(String value) {
    String[] parts = value.strip().split(":");
    if (parts.length != 3) {
      throw new IllegalArgumentException("Not a GTFS time: " + value);
    }
    return Integer.parseInt(parts[0]) * 3600 + Integer.parseInt(parts[1]) * 60 + Integer.parseInt(parts[2]);
  }

  /** gb-transit writes these dates {@code YYYY-MM-DD}, not the {@code YYYYMMDD} GTFS uses elsewhere; take either. */
  static ServiceDate parseDate(String value) {
    try {
      return ServiceDate.parseString(value.strip().replace("-", ""));
    } catch (java.text.ParseException e) {
      throw new IllegalArgumentException("Not a date: " + value, e);
    }
  }

  private static @Nullable Integer parseInt(@Nullable String value) {
    return isBlank(value) ? null : Integer.valueOf(value.strip());
  }

  private static @Nullable String strip(@Nullable String value) {
    return value == null ? null : value.strip();
  }

  private static boolean isBlank(@Nullable String value) {
    return value == null || value.isBlank();
  }
}
