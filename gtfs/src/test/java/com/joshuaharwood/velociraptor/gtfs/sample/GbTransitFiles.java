package com.joshuaharwood.velociraptor.gtfs.sample;

import com.joshuaharwood.velociraptor.gtfs.sample.SampleFeed.Call;
import com.joshuaharwood.velociraptor.gtfs.sample.SampleFeed.Link;
import com.joshuaharwood.velociraptor.gtfs.sample.SampleFeed.Shape;
import com.joshuaharwood.velociraptor.gtfs.sample.SampleFeed.Station;
import com.joshuaharwood.velociraptor.gtfs.sample.SampleFeed.TflStation;
import com.joshuaharwood.velociraptor.gtfs.sample.SampleFeed.Trip;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.joshuaharwood.velociraptor.gtfs.sample.SampleFeed.CALENDARS;
import static com.joshuaharwood.velociraptor.gtfs.sample.SampleFeed.FEED_END;
import static com.joshuaharwood.velociraptor.gtfs.sample.SampleFeed.FEED_START;
import static com.joshuaharwood.velociraptor.gtfs.sample.SampleFeed.LINKS;
import static com.joshuaharwood.velociraptor.gtfs.sample.SampleFeed.OPERATORS;
import static com.joshuaharwood.velociraptor.gtfs.sample.SampleFeed.REGULAR;
import static com.joshuaharwood.velociraptor.gtfs.sample.SampleFeed.STATIONS;
import static com.joshuaharwood.velociraptor.gtfs.sample.SampleFeed.TFL_AGENCY;
import static com.joshuaharwood.velociraptor.gtfs.sample.SampleFeed.TFL_INTERCHANGE;
import static com.joshuaharwood.velociraptor.gtfs.sample.SampleFeed.TFL_ROUTE;
import static com.joshuaharwood.velociraptor.gtfs.sample.SampleFeed.TFL_SERVICE;
import static com.joshuaharwood.velociraptor.gtfs.sample.SampleFeed.TZ;
import static com.joshuaharwood.velociraptor.gtfs.sample.SampleFeed.VICTORIA_LINE;
import static com.joshuaharwood.velociraptor.gtfs.sample.SampleFeed.WARREN_STREET_WALK;
import static com.joshuaharwood.velociraptor.gtfs.sample.SampleFeed.compact;
import static com.joshuaharwood.velociraptor.gtfs.sample.SampleFeed.flag;
import static com.joshuaharwood.velociraptor.gtfs.sample.SampleFeed.hm;
import static com.joshuaharwood.velociraptor.gtfs.sample.SampleFeed.m;
import static com.joshuaharwood.velociraptor.gtfs.sample.SampleFeed.row;
import static com.joshuaharwood.velociraptor.gtfs.sample.SampleFeed.time;
import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;

/**
 * {@link SampleFeed} written as gb-transit publishes a feed. What changes from the dtd2gtfs shape, and nothing
 * else - the timetable, calendars and links are the same network:
 * <ul>
 *   <li>a station is {@code 910G} + TIPLOC with {@code location_type} 1 and its CRS in {@code stop_code}; every call is
 *       at a boarding point beneath it, {@code 9100} + TIPLOC + platform, or {@code 9100} + TIPLOC with no platform;
 *   <li>{@code agency_id} is the National Operator Code form, {@code =SN}; {@code route_id} the ATOC code;
 *   <li>{@code trip_id} is the UID and the schedule's start and end dates, {@code WB0900_20260601_20260628}, and
 *       {@code trip_headsign} the destination's name;
 *   <li>fixed links are {@code transfers.txt} rows between two stations with gb-transit's extension columns, one per
 *       pair: the envelope of that pair's windows, so a pair open for different hours on a Sunday is published as
 *       open for the longer hours every day;
 *   <li>{@code transfers.txt} also carries a split/join row ({@code transfer_type} 4), and the feed carries the
 *       {@code areas.txt}/{@code stop_areas.txt} group stations - neither is routed on, both must be read past.
 * </ul>
 * The rail and TfL shape leaves out the Tube links and adds a timetabled Victoria line: its platforms at VIC, EUS and
 * STP are children of the rail stations, and Green Park, Oxford Circus and Warren Street are TfL stations of their
 * own, with a walk from Warren Street to Euston that has no window.
 */
final class GbTransitFiles {

  private static final String TRANSFERS_HEADER = "from_stop_id,to_stop_id,from_trip_id,to_trip_id,transfer_type,"
    + "min_transfer_time,mode,start_time,end_time,start_date,end_date,monday,tuesday,wednesday,thursday,friday,"
    + "saturday,sunday\n";

  private static final Map<String, Station> BY_CRS = STATIONS.stream()
    .collect(Collectors.toMap(Station::crs, Function.identity()));

  private GbTransitFiles() {}

  static Map<String, String> files(Shape shape) {
    boolean tfl = shape == Shape.GB_TRANSIT_RAIL_AND_TFL;
    var files = new LinkedHashMap<String, String>();
    files.put("agency.txt", agency(tfl));
    files.put("areas.txt", "area_id,area_name\n1072,London Terminals\n");
    files.put("calendar.txt", SampleFeed.calendar() + (tfl ? tflCalendar() : ""));
    files.put("calendar_dates.txt", SampleFeed.calendarDates());
    files.put("feed_info.txt", SampleFeed.feedInfo());
    files.put("routes.txt", routes(tfl));
    files.put("stop_areas.txt", stopAreas());
    files.put("stop_times.txt", stopTimes(tfl));
    files.put("stops.txt", stops(tfl));
    files.put("transfers.txt", transfers(tfl));
    files.put("trips.txt", trips(tfl));
    return files;
  }

  static String tripId(Trip trip) {
    var calendar = CALENDARS.stream().filter(c -> c.serviceId() == trip.serviceId()).findFirst().orElseThrow();
    return trip.uid() + "_" + compact(calendar.start()) + "_" + compact(calendar.end());
  }

  /** The fixed links as gb-transit publishes them: one row per pair, the envelope of its windows. */
  static List<Link> envelopes(boolean tfl) {
    var byPair = new LinkedHashMap<String, List<Link>>();
    for (var link : LINKS) {
      if (tfl && link.mode().equals("TUBE")) {
        continue;
      }
      byPair.computeIfAbsent(link.from() + "," + link.to(), _ -> new ArrayList<>()).add(link);
    }
    var envelopes = new ArrayList<Link>();
    for (var links : byPair.values()) {
      var days = EnumSet.noneOf(DayOfWeek.class);
      links.forEach(l -> days.addAll(l.days()));
      var modes = links.stream().map(Link::mode).collect(Collectors.toCollection(TreeSet::new));
      envelopes.add(new Link(links.getFirst().from(), links.getFirst().to(), String.join("|", modes),
        links.stream().mapToInt(Link::duration).min().orElseThrow(),
        links.stream().mapToInt(Link::start).min().orElseThrow(),
        links.stream().mapToInt(Link::end).max().orElseThrow(),
        days));
    }
    return envelopes;
  }

  private static String agency(boolean tfl) {
    var sb = new StringBuilder("agency_id,agency_name,agency_url,agency_timezone,agency_lang,agency_phone,agency_fare_url\n");
    for (var op : OPERATORS) {
      row(sb, "=" + op.code(), op.name(), op.url(), TZ, "en", op.phone(), "");
    }
    if (tfl) {
      row(sb, TFL_AGENCY, "London Underground", "https://tfl.gov.uk/modes/tube/", TZ, "en", "0343 222 1234", "");
    }
    return sb.toString();
  }

  private static String tflCalendar() {
    var sb = new StringBuilder();
    row(sb, TFL_SERVICE, 1, 1, 1, 1, 1, 1, 1, compact(FEED_START), compact(FEED_END));
    return sb.toString();
  }

  private static String routes(boolean tfl) {
    var sb = new StringBuilder("route_id,agency_id,route_short_name,route_long_name,route_type,route_text_color,route_color,route_url,route_desc\n");
    for (var op : OPERATORS) {
      row(sb, op.code(), "=" + op.code(), "", op.name(), 2, "FFFFFF", "000000", "", "");
    }
    if (tfl) {
      row(sb, TFL_ROUTE, TFL_AGENCY, "Victoria", "Victoria", 1, "000000", "039BE5", "https://tfl.gov.uk/tube/route/victoria/", "");
    }
    return sb.toString();
  }

  private static String stopAreas() {
    var sb = new StringBuilder("area_id,stop_id\n");
    for (var crs : List.of("EUS", "LBG", "STP", "VIC")) {
      row(sb, 1072, BY_CRS.get(crs).atco());
    }
    return sb.toString();
  }

  private static String stops(boolean tfl) {
    // stop_id -> row, so the file comes out sorted by id as gb-transit writes it.
    var rows = new TreeMap<String, Object[]>();
    for (var s : STATIONS) {
      rows.put(s.atco(), new Object[]{s.atco(), s.crs(), s.name(), s.cate(), "", "", 1, "", "", TZ, 0, s.lon(), s.lat()});
    }
    for (var trip : SampleFeed.trips()) {
      for (var call : trip.calls()) {
        var s = BY_CRS.get(call.crs());
        var id = s.boardingPoint(call.platform());
        var name = call.platform().isEmpty() ? s.name() : s.name() + " Platform " + call.platform();
        rows.putIfAbsent(id, new Object[]{id, s.crs(), name, s.cate(), "", "", 0, s.atco(), call.platform(), TZ, 0, s.lon(), s.lat()});
      }
    }
    if (tfl) {
      for (var t : VICTORIA_LINE) {
        String parent;
        if (t.within() == null) {
          parent = t.atco();
          rows.put(t.atco(), new Object[]{t.atco(), t.code(), t.name(), "", "", "", 1, "", "", TZ, 0, t.lon(), t.lat()});
        } else {
          parent = BY_CRS.get(t.within()).atco();
        }
        rows.put(t.platform(), new Object[]{t.platform(), "", t.name(), "", "", "", 0, parent, "", TZ, 0, t.lon(), t.lat()});
      }
    }
    var sb = new StringBuilder("stop_id,stop_code,stop_name,stop_desc,zone_id,stop_url,location_type,parent_station,platform_code,stop_timezone,wheelchair_boarding,stop_lon,stop_lat\n");
    rows.values().forEach(r -> row(sb, r));
    return sb.toString();
  }

  private static String trips(boolean tfl) {
    var sb = new StringBuilder("route_id,service_id,trip_id,trip_headsign,trip_short_name,direction_id,wheelchair_accessible,bikes_allowed,shape_id\n");
    for (var t : SampleFeed.trips()) {
      row(sb, t.pattern().operator().code(), t.serviceId(), tripId(t), BY_CRS.get(t.calls().getLast().crs()).name(),
        t.headcode(), 0, 0, 0, "");
    }
    if (tfl) {
      for (var t : tflTrips()) {
        row(sb, TFL_ROUTE, TFL_SERVICE, t.id(), t.calls().getLast().name(), "", t.northbound() ? 0 : 1, 0, 0, "");
      }
    }
    return sb.toString();
  }

  private static String stopTimes(boolean tfl) {
    var sb = new StringBuilder("trip_id,arrival_time,departure_time,stop_id,stop_sequence,stop_headsign,pickup_type,drop_off_type,shape_dist_traveled,timepoint\n");
    for (var t : SampleFeed.trips()) {
      int seq = 1;
      for (Call c : t.calls()) {
        row(sb, tripId(t), time(c.arrival()), time(c.departure()), BY_CRS.get(c.crs()).boardingPoint(c.platform()),
          seq++, "", c.pickup(), c.dropOff(), "", 1);
      }
    }
    if (tfl) {
      for (var t : tflTrips()) {
        for (int i = 0; i < t.calls().size(); i++) {
          row(sb, t.id(), time(t.times().get(i)), time(t.times().get(i)), t.calls().get(i).platform(), i + 1, "",
            REGULAR, REGULAR, "", 1);
        }
      }
    }
    return sb.toString();
  }

  private static String transfers(boolean tfl) {
    var sb = new StringBuilder(TRANSFERS_HEADER);
    for (var s : STATIONS) {
      if (s.hasInterchangeRow()) {
        row(sb, s.atco(), s.atco(), "", "", 2, s.interchange(), "", "", "", "", "", "", "", "", "", "", "", "");
      }
    }
    for (var l : envelopes(tfl)) {
      row(sb, BY_CRS.get(l.from()).atco(), BY_CRS.get(l.to()).atco(), "", "", 2, l.duration(), l.mode(),
        time(l.start()), time(l.end()), "2026-01-01", "2026-12-31",
        flag(l.days(), MONDAY), flag(l.days(), TUESDAY), flag(l.days(), WEDNESDAY), flag(l.days(), THURSDAY),
        flag(l.days(), FRIDAY), flag(l.days(), SATURDAY), flag(l.days(), SUNDAY));
    }
    // A stand-in coupling, so the reader has a split/join row to read past: the 10:00 fast from VIC and the 11:25
    // West Coastway from BTN, as though they were one train dividing there.
    var fast = trip(SampleFeed.SN_FAST_DOWN, hm(10, 0));
    var coastway = trip(SampleFeed.SN_WEST_OUT, hm(11, 25));
    var point = BY_CRS.get("BTN").boardingPoint(fast.calls().getLast().platform());
    row(sb, point, point, tripId(fast), tripId(coastway), 4, "", "", "", "", "", "", "", "", "", "", "", "", "");
    if (tfl) {
      for (var t : VICTORIA_LINE) {
        if (t.within() == null) {
          row(sb, t.atco(), t.atco(), "", "", 2, TFL_INTERCHANGE, "", "", "", "", "", "", "", "", "", "", "", "");
        }
      }
      var walk = WARREN_STREET_WALK;
      var euston = BY_CRS.get(walk.to()).atco();
      row(sb, walk.from(), euston, "", "", 2, walk.duration(), walk.mode(), "", "", "", "", "", "", "", "", "", "", "");
      row(sb, euston, walk.from(), "", "", 2, walk.duration(), walk.mode(), "", "", "", "", "", "", "", "", "", "", "");
    }
    return sb.toString();
  }

  private static Trip trip(SampleFeed.Pattern pattern, int departure) {
    return SampleFeed.trips().stream()
      .filter(t -> t.pattern() == pattern && t.departure() == departure)
      .findFirst().orElseThrow();
  }

  record TflTrip(String id, boolean northbound, List<TflStation> calls, List<Integer> times) {}

  /** Every ten minutes from 05:30 to 24:30 each way, daily. */
  static List<TflTrip> tflTrips() {
    var trips = new ArrayList<TflTrip>();
    var south = VICTORIA_LINE.reversed();
    for (int dep = hm(5, 30); dep <= hm(24, 30); dep += m(10)) {
      var north = new ArrayList<Integer>();
      var southTimes = new ArrayList<Integer>();
      int[] minutes = {0, 2, 4, 6, 8, 10};
      for (int i = 0; i < minutes.length; i++) {
        north.add(dep + m(minutes[i]));
        southTimes.add(dep + m(minutes[minutes.length - 1] - minutes[minutes.length - 1 - i]));
      }
      trips.add(new TflTrip("tfl_VIC_N_" + SampleFeed.hhmm(dep), true, VICTORIA_LINE, north));
      trips.add(new TflTrip("tfl_VIC_S_" + SampleFeed.hhmm(dep), false, south, southTimes));
    }
    return trips;
  }
}
