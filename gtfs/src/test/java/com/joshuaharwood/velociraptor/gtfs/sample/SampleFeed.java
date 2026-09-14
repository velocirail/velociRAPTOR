package com.joshuaharwood.velociraptor.gtfs.sample;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;

/**
 * The artificial golden GTFS feed: an invented timetable over a real corner of the network, written in the shape
 * the bridge reads today (dtd2gtfs style - {@code stop_id} is the CRS code, the train UID rides in
 * {@code trip_headsign}, fixed links are one {@code links.txt} row per window, interchange is a self-transfer per
 * station).
 * <p>
 * Stations, CRS codes, operators and geography are real so that queries read like the ones the service answers;
 * every time, calendar and link is made up here, so nothing is derived from a licensed extract. The committed copy
 * lives in {@code fixtures/gtfs-sample}; {@link SampleFeedGoldenTest} keeps it equal to what this class writes and
 * regenerates it on request.
 *
 * <h2>The network</h2>
 * <pre>
 *   MKC -- WFJ -- EUS                                   London Northwestern (LM): the WCML stub
 *                  :  TUBE / WALK between the termini
 *   STP -- ZFD -- LBG          VIC                      Thameslink (TL) / Southern (SN) / Gatwick Express (GX)
 *                   \          |
 *                   ECR ------ CLJ
 *                    |
 *                   RDH -- GTW -- TBD -- HHE
 *                                          |
 *   PMH -- PMS -- FTN -- HAV -- CCH -- WRH -- PLD -- HOV -- BTN -- LWS -- EBN -- BEX -- HGS -- ORE ..TOK..DLH..WSE.. RYE -- APD -- HMT -- AFK ~ ASI
 *    ~ FERRY                                  West Coastway            East Coastway          Marshlink (request stops)
 *   RYP -- RYD -- SHN                                  Island Line (SW)
 * </pre>
 * <ul>
 *   <li>SN fast VIC-ECR-GTW-HHE-BTN, 60 min, hourly at xx:00 both ways. Even hours run daily, odd hours Mon-Sat.
 *   <li>SN stopping VIC-CLJ-ECR-RDH-GTW-TBD-HHE-BTN, 85 min, at xx:15/xx:45 from VIC and xx:10/xx:40 from BTN, Mon-Sat.
 *   <li>SN night owl, Saturday only: VIC 24:15 (00:15 Sunday, owned by the Saturday service day), set-down only at
 *       ECR; the return is pick-up only at ECR.
 *   <li>SN engineering overlay: the 22:00 fast from VIC is withdrawn Mon-Fri 22-26 June and a 22:05 stopper runs
 *       instead, so a diff of those days shows one trip stop and another start.
 *   <li>GX VIC-GTW non-stop, 30 min, xx:30 from VIC and xx:35 from GTW, daily.
 *   <li>TL STP-ZFD-LBG-ECR-GTW-TBD-HHE-BTN, 75 min, xx:10 from STP and xx:20 from BTN, daily.
 *   <li>SN West Coastway BTN-HOV-PLD-WRH-CCH-HAV-FTN-PMS-PMH, 70 min, xx:25 from BTN and xx:05 from PMH.
 *   <li>SN East Coastway BTN-LWS-EBN-BEX-HGS, 60 min, xx:33 from BTN and xx:05 from HGS.
 *   <li>SN Marshlink HGS-ORE-TOK-DLH-WSE-RYE-APD-HMT-AFK, 55 min, xx:45 from HGS and xx:05 from AFK. TOK, DLH and WSE
 *       are request stops (pickup/drop-off 3) with no interchange row. The AFK train reaches HGS at xx:00 and the
 *       coastway leaves at xx:05 with a 4 min interchange, so that connection is made by one minute.
 *   <li>SW Island Line RYP-RYD-SHN, 25 min, xx:20 from RYP and xx:50 from SHN, daily.
 *   <li>LM EUS-WFJ-MKC, 35 min, xx:50 from EUS and xx:10 from MKC, daily. Reached from the south only by a Tube leg
 *       between termini, so BTN to MKC is the canonical train-link-train journey.
 *   <li>Fixed links: TUBE between VIC, LBG, STP and EUS (Mon-Sat 05:30-24:30, Sun 07:00-23:30); WALK STP-EUS
 *       (always); FERRY PMH-RYP (Mon-Sat 06:00-23:00, Sun 08:00-20:00); WALK AFK-ASI (always). ASI has no trains.
 *   <li>Calendar: 2026-06-01 (Mon) to 2026-06-28 (Sun). 2026-06-15 is a bank holiday: the Mon-Sat services are
 *       removed by calendar_dates, leaving the daily (Sunday-pattern) timetable.
 * </ul>
 * Identifiers are formulas, so adding a train never renumbers another. {@code trip_id} is the operator digit, the
 * two-digit pattern index and the departure hhmm (the 09:00 fast from BTN is {@code 1020900}); the UID is the
 * operator letter, the pattern letter and the hhmm ({@code WB0900}).
 */
public final class SampleFeed {

  public static final LocalDate FEED_START = LocalDate.of(2026, 6, 1);
  public static final LocalDate FEED_END = LocalDate.of(2026, 6, 28);
  public static final LocalDate BANK_HOLIDAY = LocalDate.of(2026, 6, 15);
  public static final LocalDate OVERLAY_START = LocalDate.of(2026, 6, 22);
  public static final LocalDate OVERLAY_END = LocalDate.of(2026, 6, 26);

  private static final Set<DayOfWeek> EVERY_DAY = EnumSet.allOf(DayOfWeek.class);
  private static final Set<DayOfWeek> MON_TO_SAT = EnumSet.range(MONDAY, SATURDAY);
  private static final Set<DayOfWeek> MON_TO_FRI = EnumSet.range(MONDAY, FRIDAY);
  private static final Set<DayOfWeek> SUN = EnumSet.of(SUNDAY);

  // service ids
  static final int MON_SAT = 1;
  static final int SAT_ONLY = 2;
  static final int DAILY = 3;
  static final int MON_SAT_OVERLAY_BASE = 4;
  static final int OVERLAY = 5;

  // pickup / drop-off types
  private static final int REGULAR = 0;
  private static final int NONE = 1;
  private static final int REQUEST = 3;

  private static final String TZ = "Europe/London";

  /** {@code interchange < 0} means the station gets no transfers.txt row, so the reader's default (0) applies. */
  record Station(String crs, String tiploc, String name, int cate, double lat, double lon, int interchange) {
    boolean hasInterchangeRow() {
      return interchange >= 0;
    }
  }

  record Operator(int tripDigit, char uidLetter, String code, String name, String url, String phone) {}

  /** One stopping pattern in one direction. {@code index} and {@code code} make the trip id and UID. */
  record Pattern(Operator operator, int index, char code, String routeId, String shortName, String longName, String desc) {}

  record Calendar(int serviceId, Set<DayOfWeek> days, LocalDate start, LocalDate end) {}

  record CalendarException(int serviceId, LocalDate date, int type) {}

  record Call(String crs, int arrival, int departure, int pickup, int dropOff, String platform) {}

  record Trip(Pattern pattern, int serviceId, List<Call> calls) {
    String id() {
      return pattern.operator().tripDigit() + String.format("%02d", pattern.index()) + hhmm(departure());
    }

    String uid() {
      return "" + pattern.operator().uidLetter() + pattern.code() + hhmm(departure());
    }

    String headcode() {
      return pattern.operator().code() + pattern.code() + String.format("%02d", (departure() / 3600) % 24);
    }

    int departure() {
      return calls.getFirst().departure();
    }
  }

  record Link(String from, String to, String mode, int duration, int start, int end, Set<DayOfWeek> days) {}

  static final Operator SN = new Operator(1, 'W', "SN", "Southern", "https://www.southernrailway.com/", "0345 127 2920");
  static final Operator TL = new Operator(2, 'L', "TL", "Thameslink", "https://www.thameslinkrailway.com/", "0345 026 4700");
  static final Operator GX = new Operator(3, 'G', "GX", "Gatwick Express", "https://www.gatwickexpress.com/", "0345 850 1530");
  static final Operator SW = new Operator(4, 'S', "SW", "South Western Railway", "https://www.southwesternrailway.com/", "0345 600 0650");
  static final Operator LM = new Operator(5, 'M', "LM", "London Northwestern Railway", "https://www.londonnorthwesternrailway.co.uk/", "0333 311 0006");

  static final List<Operator> OPERATORS = List.of(SN, TL, GX, SW, LM);

  /** Real stations with approximate coordinates; interchange times are invented but plausible. */
  static final List<Station> STATIONS = List.of(
    new Station("VIC", "VICTRIC", "London Victoria", 3, 51.4952, -0.1441, 600),
    new Station("LBG", "LNDNBDC", "London Bridge", 3, 51.5050, -0.0864, 480),
    new Station("STP", "STPX", "London St Pancras International", 3, 51.5316, -0.1263, 600),
    new Station("ZFD", "FRNDNLT", "Farringdon", 2, 51.5203, -0.1053, 300),
    new Station("EUS", "EUSTON", "London Euston", 3, 51.5282, -0.1337, 600),
    new Station("CLJ", "CLPHMJC", "Clapham Junction", 3, 51.4642, -0.1705, 300),
    new Station("ECR", "ECROYDN", "East Croydon", 3, 51.3756, -0.0928, 300),
    new Station("RDH", "REDHILL", "Redhill", 2, 51.2402, -0.1656, 180),
    new Station("GTW", "GTWK", "Gatwick Airport", 3, 51.1564, -0.1610, 300),
    new Station("TBD", "THBDGS", "Three Bridges", 2, 51.1169, -0.1622, 240),
    new Station("HHE", "HYWRDSH", "Haywards Heath", 2, 50.9958, -0.1057, 240),
    new Station("BTN", "BRGHTN", "Brighton", 3, 50.8290, -0.1411, 300),
    new Station("HOV", "HOVE", "Hove", 1, 50.8354, -0.1710, 180),
    new Station("PLD", "PORTSLD", "Portslade", 0, 50.8391, -0.2065, 120),
    new Station("WRH", "WORTHNG", "Worthing", 1, 50.8186, -0.3766, 180),
    new Station("CCH", "CHCHSTR", "Chichester", 1, 50.8320, -0.7818, 180),
    new Station("HAV", "HAVANT", "Havant", 2, 50.8544, -0.9819, 180),
    new Station("FTN", "FRATTON", "Fratton", 1, 50.7962, -1.0736, 120),
    new Station("PMS", "PMTH", "Portsmouth & Southsea", 2, 50.7985, -1.0906, 180),
    new Station("PMH", "PMTHHRB", "Portsmouth Harbour", 3, 50.7963, -1.1083, 180),
    new Station("RYP", "RYDPIER", "Ryde Pier Head", 2, 50.7375, -1.1594, 120),
    new Station("RYD", "RYDE", "Ryde Esplanade", 1, 50.7316, -1.1590, 120),
    new Station("SHN", "SHANKLN", "Shanklin", 1, 50.6338, -1.1793, 120),
    new Station("LWS", "LEWES", "Lewes", 2, 50.8757, 0.0093, 180),
    new Station("EBN", "EBOURNE", "Eastbourne", 2, 50.7695, 0.2812, 300),
    new Station("BEX", "BEXHILL", "Bexhill", 1, 50.8397, 0.4718, 120),
    new Station("HGS", "HASTING", "Hastings", 3, 50.8577, 0.5757, 240),
    new Station("ORE", "ORE", "Ore", 0, 50.8683, 0.6079, 120),
    new Station("TOK", "THROAKS", "Three Oaks", 0, 50.8998, 0.6224, -1),
    new Station("DLH", "DOLEHAM", "Doleham", 0, 50.9160, 0.6440, -1),
    new Station("WSE", "WNCHLSA", "Winchelsea", 0, 50.9223, 0.6997, -1),
    new Station("RYE", "RYE", "Rye", 1, 50.9518, 0.7331, 120),
    new Station("APD", "APLDORE", "Appledore (Kent)", 0, 51.0177, 0.8060, 120),
    new Station("HMT", "HAMSTRT", "Ham Street", 0, 51.0665, 0.8546, 120),
    new Station("AFK", "ASHFKY", "Ashford International", 3, 51.1435, 0.8760, 300),
    new Station("ASI", "ASHFKI", "Ashford International (Eurostar)", 3, 51.1435, 0.8760, 120),
    new Station("WFJ", "WATFDJ", "Watford Junction", 3, 51.6636, -0.3965, 240),
    new Station("MKC", "MKNSCEN", "Milton Keynes Central", 3, 52.0345, -0.7740, 300)
  );

  private static final String STD = "Train. Standard class only. Reservation not possible";
  private static final String FIRST = "Train. First class available. Reservation possible";

  static final Pattern SN_FAST_DOWN = new Pattern(SN, 1, 'A', "SN_FAST_VIC_BTN", "SN:VIC->BTN", "Southern fast service from London Victoria to Brighton", STD);
  static final Pattern SN_FAST_UP = new Pattern(SN, 2, 'B', "SN_FAST_BTN_VIC", "SN:BTN->VIC", "Southern fast service from Brighton to London Victoria", STD);
  static final Pattern SN_STOP_DOWN = new Pattern(SN, 3, 'C', "SN_STOP_VIC_BTN", "SN:VIC->BTN", "Southern stopping service from London Victoria to Brighton", STD);
  static final Pattern SN_STOP_UP = new Pattern(SN, 4, 'D', "SN_STOP_BTN_VIC", "SN:BTN->VIC", "Southern stopping service from Brighton to London Victoria", STD);
  static final Pattern SN_NIGHT_DOWN = new Pattern(SN, 5, 'E', "SN_NIGHT_VIC_BTN", "SN:VIC->BTN", "Southern night service from London Victoria to Brighton", STD);
  static final Pattern SN_NIGHT_UP = new Pattern(SN, 6, 'F', "SN_NIGHT_BTN_VIC", "SN:BTN->VIC", "Southern night service from Brighton to London Victoria", STD);
  static final Pattern SN_OVERLAY_DOWN = new Pattern(SN, 7, 'G', "SN_STOP_VIC_BTN", "SN:VIC->BTN", "Southern stopping service from London Victoria to Brighton", STD);
  static final Pattern SN_WEST_OUT = new Pattern(SN, 8, 'H', "SN_BTN_PMH", "SN:BTN->PMH", "Southern West Coastway from Brighton to Portsmouth Harbour", STD);
  static final Pattern SN_WEST_BACK = new Pattern(SN, 9, 'J', "SN_PMH_BTN", "SN:PMH->BTN", "Southern West Coastway from Portsmouth Harbour to Brighton", STD);
  static final Pattern SN_EAST_OUT = new Pattern(SN, 10, 'K', "SN_BTN_HGS", "SN:BTN->HGS", "Southern East Coastway from Brighton to Hastings", STD);
  static final Pattern SN_EAST_BACK = new Pattern(SN, 11, 'L', "SN_HGS_BTN", "SN:HGS->BTN", "Southern East Coastway from Hastings to Brighton", STD);
  static final Pattern SN_MARSH_OUT = new Pattern(SN, 12, 'M', "SN_HGS_AFK", "SN:HGS->AFK", "Southern Marshlink from Hastings to Ashford International", STD);
  static final Pattern SN_MARSH_BACK = new Pattern(SN, 13, 'N', "SN_AFK_HGS", "SN:AFK->HGS", "Southern Marshlink from Ashford International to Hastings", STD);
  static final Pattern TL_DOWN = new Pattern(TL, 1, 'A', "TL_STP_BTN", "TL:STP->BTN", "Thameslink from London St Pancras to Brighton", STD);
  static final Pattern TL_UP = new Pattern(TL, 2, 'B', "TL_BTN_STP", "TL:BTN->STP", "Thameslink from Brighton to London St Pancras", STD);
  static final Pattern GX_DOWN = new Pattern(GX, 1, 'A', "GX_VIC_GTW", "GX:VIC->GTW", "Gatwick Express from London Victoria to Gatwick Airport", FIRST);
  static final Pattern GX_UP = new Pattern(GX, 2, 'B', "GX_GTW_VIC", "GX:GTW->VIC", "Gatwick Express from Gatwick Airport to London Victoria", FIRST);
  static final Pattern SW_OUT = new Pattern(SW, 1, 'A', "SW_RYP_SHN", "SW:RYP->SHN", "Island Line from Ryde Pier Head to Shanklin", STD);
  static final Pattern SW_BACK = new Pattern(SW, 2, 'B', "SW_SHN_RYP", "SW:SHN->RYP", "Island Line from Shanklin to Ryde Pier Head", STD);
  static final Pattern LM_OUT = new Pattern(LM, 1, 'A', "LM_EUS_MKC", "LM:EUS->MKC", "London Northwestern from London Euston to Milton Keynes Central", FIRST);
  static final Pattern LM_BACK = new Pattern(LM, 2, 'B', "LM_MKC_EUS", "LM:MKC->EUS", "London Northwestern from Milton Keynes Central to London Euston", FIRST);

  static final List<Pattern> PATTERNS = List.of(
    SN_FAST_DOWN, SN_FAST_UP, SN_STOP_DOWN, SN_STOP_UP, SN_NIGHT_DOWN, SN_NIGHT_UP, SN_OVERLAY_DOWN,
    SN_WEST_OUT, SN_WEST_BACK, SN_EAST_OUT, SN_EAST_BACK, SN_MARSH_OUT, SN_MARSH_BACK,
    TL_DOWN, TL_UP, GX_DOWN, GX_UP, SW_OUT, SW_BACK, LM_OUT, LM_BACK);

  static final List<Calendar> CALENDARS = List.of(
    new Calendar(MON_SAT, MON_TO_SAT, FEED_START, FEED_END),
    new Calendar(SAT_ONLY, EnumSet.of(SATURDAY), FEED_START, FEED_END),
    new Calendar(DAILY, EVERY_DAY, FEED_START, FEED_END),
    new Calendar(MON_SAT_OVERLAY_BASE, MON_TO_SAT, FEED_START, FEED_END),
    new Calendar(OVERLAY, MON_TO_FRI, OVERLAY_START, OVERLAY_END)
  );

  static final List<CalendarException> CALENDAR_EXCEPTIONS = calendarExceptions();

  static final List<Link> LINKS = links();

  private static final int FIRST_HOUR = 6;
  private static final int LAST_HOUR = 22;

  private SampleFeed() {}

  private static List<CalendarException> calendarExceptions() {
    var exceptions = new ArrayList<CalendarException>();
    exceptions.add(new CalendarException(MON_SAT, BANK_HOLIDAY, 2));
    exceptions.add(new CalendarException(MON_SAT_OVERLAY_BASE, BANK_HOLIDAY, 2));
    for (LocalDate d = OVERLAY_START; !d.isAfter(OVERLAY_END); d = d.plusDays(1)) {
      exceptions.add(new CalendarException(MON_SAT_OVERLAY_BASE, d, 2));
    }
    return List.copyOf(exceptions);
  }

  private static List<Link> links() {
    var links = new ArrayList<Link>();
    // Tube between the London termini: a Mon-Sat window and a shorter Sunday one, as the real links.txt does.
    for (var pair : List.of(new String[]{"VIC", "LBG", "1500"}, new String[]{"VIC", "STP", "1200"},
      new String[]{"VIC", "EUS", "900"}, new String[]{"LBG", "STP", "900"}, new String[]{"LBG", "EUS", "1200"})) {
      int duration = Integer.parseInt(pair[2]);
      links.add(new Link(pair[0], pair[1], "TUBE", duration, hm(5, 30), hm(24, 30), MON_TO_SAT));
      links.add(new Link(pair[1], pair[0], "TUBE", duration, hm(5, 30), hm(24, 30), MON_TO_SAT));
      links.add(new Link(pair[0], pair[1], "TUBE", duration, hm(7, 0), hm(23, 30), SUN));
      links.add(new Link(pair[1], pair[0], "TUBE", duration, hm(7, 0), hm(23, 30), SUN));
    }
    links.add(new Link("STP", "EUS", "WALK", 720, hm(0, 1), hm(23, 59), EVERY_DAY));
    links.add(new Link("EUS", "STP", "WALK", 720, hm(0, 1), hm(23, 59), EVERY_DAY));
    links.add(new Link("PMH", "RYP", "FERRY", 1320, hm(6, 0), hm(23, 0), MON_TO_SAT));
    links.add(new Link("RYP", "PMH", "FERRY", 1320, hm(6, 0), hm(23, 0), MON_TO_SAT));
    links.add(new Link("PMH", "RYP", "FERRY", 1320, hm(8, 0), hm(20, 0), SUN));
    links.add(new Link("RYP", "PMH", "FERRY", 1320, hm(8, 0), hm(20, 0), SUN));
    links.add(new Link("AFK", "ASI", "WALK", 300, hm(0, 1), hm(23, 59), EVERY_DAY));
    links.add(new Link("ASI", "AFK", "WALK", 300, hm(0, 1), hm(23, 59), EVERY_DAY));
    return List.copyOf(links);
  }

  /** Every trip, sorted by id. */
  static List<Trip> trips() {
    var trips = new ArrayList<Trip>();
    for (int h = FIRST_HOUR; h <= LAST_HOUR; h++) {
      int hourly = h % 2 == 0 ? DAILY : MON_SAT;
      trips.add(new Trip(SN_FAST_DOWN, h == LAST_HOUR ? MON_SAT_OVERLAY_BASE : hourly, fastDown(hm(h, 0))));
      trips.add(new Trip(SN_FAST_UP, hourly, fastUp(hm(h, 0))));
      trips.add(new Trip(SN_STOP_DOWN, MON_SAT, stopDown(hm(h, 15))));
      trips.add(new Trip(SN_STOP_DOWN, MON_SAT, stopDown(hm(h, 45))));
      trips.add(new Trip(SN_STOP_UP, MON_SAT, stopUp(hm(h, 10))));
      trips.add(new Trip(SN_STOP_UP, MON_SAT, stopUp(hm(h, 40))));
      trips.add(new Trip(SN_WEST_OUT, hourly, westOut(hm(h, 25))));
      trips.add(new Trip(SN_WEST_BACK, hourly, westBack(hm(h, 5))));
      trips.add(new Trip(SN_EAST_OUT, hourly, eastOut(hm(h, 33))));
      trips.add(new Trip(SN_EAST_BACK, hourly, eastBack(hm(h, 5))));
      trips.add(new Trip(SN_MARSH_OUT, hourly, marshOut(hm(h, 45))));
      trips.add(new Trip(SN_MARSH_BACK, hourly, marshBack(hm(h, 5))));
      trips.add(new Trip(TL_DOWN, DAILY, thameslinkDown(hm(h, 10))));
      trips.add(new Trip(TL_UP, DAILY, thameslinkUp(hm(h, 20))));
      trips.add(new Trip(GX_DOWN, DAILY, List.of(
        new Call("VIC", hm(h, 30), hm(h, 30), REGULAR, NONE, "13"),
        new Call("GTW", hm(h, 30) + m(30), hm(h, 30) + m(30), NONE, REGULAR, "7"))));
      trips.add(new Trip(GX_UP, DAILY, List.of(
        new Call("GTW", hm(h, 35), hm(h, 35), REGULAR, NONE, "7"),
        new Call("VIC", hm(h, 35) + m(30), hm(h, 35) + m(30), NONE, REGULAR, "13"))));
      trips.add(new Trip(SW_OUT, DAILY, List.of(
        new Call("RYP", hm(h, 20), hm(h, 20), REGULAR, NONE, ""),
        new Call("RYD", hm(h, 20) + m(3), hm(h, 20) + m(4), REGULAR, REGULAR, ""),
        new Call("SHN", hm(h, 20) + m(25), hm(h, 20) + m(25), NONE, REGULAR, ""))));
      trips.add(new Trip(SW_BACK, DAILY, List.of(
        new Call("SHN", hm(h, 50), hm(h, 50), REGULAR, NONE, ""),
        new Call("RYD", hm(h, 50) + m(21), hm(h, 50) + m(22), REGULAR, REGULAR, ""),
        new Call("RYP", hm(h, 50) + m(25), hm(h, 50) + m(25), NONE, REGULAR, ""))));
      trips.add(new Trip(LM_OUT, DAILY, List.of(
        new Call("EUS", hm(h, 50), hm(h, 50), REGULAR, NONE, "8"),
        new Call("WFJ", hm(h, 50) + m(17), hm(h, 50) + m(18), REGULAR, REGULAR, "9"),
        new Call("MKC", hm(h, 50) + m(35), hm(h, 50) + m(35), NONE, REGULAR, "4"))));
      trips.add(new Trip(LM_BACK, DAILY, List.of(
        new Call("MKC", hm(h, 10), hm(h, 10), REGULAR, NONE, "5"),
        new Call("WFJ", hm(h, 10) + m(17), hm(h, 10) + m(18), REGULAR, REGULAR, "8"),
        new Call("EUS", hm(h, 10) + m(35), hm(h, 10) + m(35), NONE, REGULAR, "9"))));
    }
    trips.add(new Trip(SN_NIGHT_DOWN, SAT_ONLY, List.of(
      new Call("VIC", hm(24, 15), hm(24, 15), REGULAR, NONE, "15"),
      new Call("ECR", hm(24, 31), hm(24, 32), NONE, REGULAR, "1"),
      new Call("GTW", hm(24, 46), hm(24, 47), REGULAR, REGULAR, "3"),
      new Call("HHE", hm(24, 59), hm(25, 0), REGULAR, REGULAR, "3"),
      new Call("BTN", hm(25, 15), hm(25, 15), NONE, REGULAR, "5"))));
    trips.add(new Trip(SN_NIGHT_UP, SAT_ONLY, List.of(
      new Call("BTN", hm(24, 15), hm(24, 15), REGULAR, NONE, "6"),
      new Call("HHE", hm(24, 30), hm(24, 31), REGULAR, REGULAR, "2"),
      new Call("GTW", hm(24, 43), hm(24, 44), REGULAR, REGULAR, "4"),
      new Call("ECR", hm(24, 58), hm(24, 59), REGULAR, NONE, "2"),
      new Call("VIC", hm(25, 15), hm(25, 15), NONE, REGULAR, "16"))));
    // The engineering overlay: a stopper standing in for the withdrawn 22:00 fast on 22-26 June.
    trips.add(new Trip(SN_OVERLAY_DOWN, OVERLAY, stopDown(hm(22, 5))));
    trips.sort(Comparator.comparing(Trip::id));
    return trips;
  }

  private static List<Call> fastDown(int dep) {
    return List.of(
      new Call("VIC", dep, dep, REGULAR, NONE, "15"),
      new Call("ECR", dep + m(15), dep + m(16), REGULAR, REGULAR, "1"),
      new Call("GTW", dep + m(30), dep + m(31), REGULAR, REGULAR, "3"),
      new Call("HHE", dep + m(43), dep + m(44), REGULAR, REGULAR, "3"),
      new Call("BTN", dep + m(60), dep + m(60), NONE, REGULAR, "5"));
  }

  private static List<Call> fastUp(int dep) {
    return List.of(
      new Call("BTN", dep, dep, REGULAR, NONE, "6"),
      new Call("HHE", dep + m(16), dep + m(17), REGULAR, REGULAR, "2"),
      new Call("GTW", dep + m(29), dep + m(30), REGULAR, REGULAR, "4"),
      new Call("ECR", dep + m(44), dep + m(45), REGULAR, REGULAR, "2"),
      new Call("VIC", dep + m(60), dep + m(60), NONE, REGULAR, "16"));
  }

  private static List<Call> stopDown(int dep) {
    return List.of(
      new Call("VIC", dep, dep, REGULAR, NONE, "17"),
      new Call("CLJ", dep + m(7), dep + m(8), REGULAR, REGULAR, "13"),
      new Call("ECR", dep + m(18), dep + m(19), REGULAR, REGULAR, "1"),
      new Call("RDH", dep + m(32), dep + m(33), REGULAR, REGULAR, "1"),
      new Call("GTW", dep + m(42), dep + m(43), REGULAR, REGULAR, "3"),
      new Call("TBD", dep + m(50), dep + m(51), REGULAR, REGULAR, "1"),
      new Call("HHE", dep + m(60), dep + m(61), REGULAR, REGULAR, "3"),
      new Call("BTN", dep + m(85), dep + m(85), NONE, REGULAR, "7"));
  }

  private static List<Call> stopUp(int dep) {
    return List.of(
      new Call("BTN", dep, dep, REGULAR, NONE, "8"),
      new Call("HHE", dep + m(24), dep + m(25), REGULAR, REGULAR, "2"),
      new Call("TBD", dep + m(34), dep + m(35), REGULAR, REGULAR, "2"),
      new Call("GTW", dep + m(42), dep + m(43), REGULAR, REGULAR, "4"),
      new Call("RDH", dep + m(52), dep + m(53), REGULAR, REGULAR, "2"),
      new Call("ECR", dep + m(66), dep + m(67), REGULAR, REGULAR, "2"),
      new Call("CLJ", dep + m(77), dep + m(78), REGULAR, REGULAR, "14"),
      new Call("VIC", dep + m(85), dep + m(85), NONE, REGULAR, "18"));
  }

  private static List<Call> thameslinkDown(int dep) {
    return List.of(
      new Call("STP", dep, dep, REGULAR, NONE, "A"),
      new Call("ZFD", dep + m(4), dep + m(5), REGULAR, REGULAR, "4"),
      new Call("LBG", dep + m(12), dep + m(13), REGULAR, REGULAR, "5"),
      new Call("ECR", dep + m(25), dep + m(26), REGULAR, REGULAR, "3"),
      new Call("GTW", dep + m(40), dep + m(41), REGULAR, REGULAR, "5"),
      new Call("TBD", dep + m(47), dep + m(48), REGULAR, REGULAR, "3"),
      new Call("HHE", dep + m(56), dep + m(57), REGULAR, REGULAR, "3"),
      new Call("BTN", dep + m(75), dep + m(75), NONE, REGULAR, "3"));
  }

  private static List<Call> thameslinkUp(int dep) {
    return List.of(
      new Call("BTN", dep, dep, REGULAR, NONE, "4"),
      new Call("HHE", dep + m(18), dep + m(19), REGULAR, REGULAR, "2"),
      new Call("TBD", dep + m(27), dep + m(28), REGULAR, REGULAR, "4"),
      new Call("GTW", dep + m(34), dep + m(35), REGULAR, REGULAR, "6"),
      new Call("ECR", dep + m(49), dep + m(50), REGULAR, REGULAR, "4"),
      new Call("LBG", dep + m(62), dep + m(63), REGULAR, REGULAR, "4"),
      new Call("ZFD", dep + m(70), dep + m(71), REGULAR, REGULAR, "3"),
      new Call("STP", dep + m(75), dep + m(75), NONE, REGULAR, "B"));
  }

  private static List<Call> westOut(int dep) {
    return List.of(
      new Call("BTN", dep, dep, REGULAR, NONE, "1"),
      new Call("HOV", dep + m(5), dep + m(6), REGULAR, REGULAR, "1"),
      new Call("PLD", dep + m(9), dep + m(10), REGULAR, REGULAR, "1"),
      new Call("WRH", dep + m(20), dep + m(21), REGULAR, REGULAR, "1"),
      new Call("CCH", dep + m(40), dep + m(41), REGULAR, REGULAR, "1"),
      new Call("HAV", dep + m(52), dep + m(53), REGULAR, REGULAR, "1"),
      new Call("FTN", dep + m(62), dep + m(63), REGULAR, REGULAR, "1"),
      new Call("PMS", dep + m(66), dep + m(67), REGULAR, REGULAR, "1"),
      new Call("PMH", dep + m(70), dep + m(70), NONE, REGULAR, "3"));
  }

  private static List<Call> westBack(int dep) {
    return List.of(
      new Call("PMH", dep, dep, REGULAR, NONE, "4"),
      new Call("PMS", dep + m(3), dep + m(4), REGULAR, REGULAR, "2"),
      new Call("FTN", dep + m(7), dep + m(8), REGULAR, REGULAR, "2"),
      new Call("HAV", dep + m(17), dep + m(18), REGULAR, REGULAR, "2"),
      new Call("CCH", dep + m(29), dep + m(30), REGULAR, REGULAR, "2"),
      new Call("WRH", dep + m(49), dep + m(50), REGULAR, REGULAR, "2"),
      new Call("PLD", dep + m(60), dep + m(61), REGULAR, REGULAR, "2"),
      new Call("HOV", dep + m(64), dep + m(65), REGULAR, REGULAR, "2"),
      new Call("BTN", dep + m(70), dep + m(70), NONE, REGULAR, "2"));
  }

  private static List<Call> eastOut(int dep) {
    return List.of(
      new Call("BTN", dep, dep, REGULAR, NONE, "2"),
      new Call("LWS", dep + m(15), dep + m(16), REGULAR, REGULAR, "3"),
      new Call("EBN", dep + m(35), dep + m(36), REGULAR, REGULAR, "2"),
      new Call("BEX", dep + m(47), dep + m(48), REGULAR, REGULAR, "1"),
      new Call("HGS", dep + m(60), dep + m(60), NONE, REGULAR, "1"));
  }

  private static List<Call> eastBack(int dep) {
    return List.of(
      new Call("HGS", dep, dep, REGULAR, NONE, "2"),
      new Call("BEX", dep + m(12), dep + m(13), REGULAR, REGULAR, "2"),
      new Call("EBN", dep + m(24), dep + m(25), REGULAR, REGULAR, "3"),
      new Call("LWS", dep + m(44), dep + m(45), REGULAR, REGULAR, "1"),
      new Call("BTN", dep + m(60), dep + m(60), NONE, REGULAR, "1"));
  }

  private static List<Call> marshOut(int dep) {
    return List.of(
      new Call("HGS", dep, dep, REGULAR, NONE, "3"),
      new Call("ORE", dep + m(4), dep + m(5), REGULAR, REGULAR, "1"),
      new Call("TOK", dep + m(10), dep + m(10), REQUEST, REQUEST, ""),
      new Call("DLH", dep + m(15), dep + m(15), REQUEST, REQUEST, ""),
      new Call("WSE", dep + m(20), dep + m(20), REQUEST, REQUEST, ""),
      new Call("RYE", dep + m(27), dep + m(28), REGULAR, REGULAR, "1"),
      new Call("APD", dep + m(37), dep + m(38), REGULAR, REGULAR, "1"),
      new Call("HMT", dep + m(45), dep + m(46), REGULAR, REGULAR, "1"),
      new Call("AFK", dep + m(55), dep + m(55), NONE, REGULAR, "1"));
  }

  private static List<Call> marshBack(int dep) {
    return List.of(
      new Call("AFK", dep, dep, REGULAR, NONE, "2"),
      new Call("HMT", dep + m(9), dep + m(10), REGULAR, REGULAR, "2"),
      new Call("APD", dep + m(17), dep + m(18), REGULAR, REGULAR, "2"),
      new Call("RYE", dep + m(27), dep + m(28), REGULAR, REGULAR, "2"),
      new Call("WSE", dep + m(35), dep + m(35), REQUEST, REQUEST, ""),
      new Call("DLH", dep + m(40), dep + m(40), REQUEST, REQUEST, ""),
      new Call("TOK", dep + m(45), dep + m(45), REQUEST, REQUEST, ""),
      new Call("ORE", dep + m(50), dep + m(51), REGULAR, REGULAR, "2"),
      new Call("HGS", dep + m(55), dep + m(55), NONE, REGULAR, "4"));
  }

  /** File name to content, in the order the files are conventionally listed. */
  public static Map<String, String> files() {
    var files = new LinkedHashMap<String, String>();
    files.put("agency.txt", agency());
    files.put("calendar.txt", calendar());
    files.put("calendar_dates.txt", calendarDates());
    files.put("feed_info.txt", feedInfo());
    files.put("links.txt", linksFile());
    files.put("routes.txt", routes());
    files.put("stop_times.txt", stopTimes());
    files.put("stops.txt", stops());
    files.put("transfers.txt", transfers());
    files.put("trips.txt", tripsFile());
    return files;
  }

  public static void writeTo(Path directory) throws IOException {
    Files.createDirectories(directory);
    for (var e : files().entrySet()) {
      Files.writeString(directory.resolve(e.getKey()), e.getValue(), StandardCharsets.UTF_8);
    }
  }

  private static String agency() {
    var sb = new StringBuilder("agency_id,agency_name,agency_url,agency_timezone,agency_lang,agency_phone,agency_fare_url\n");
    for (var op : OPERATORS) {
      row(sb, op.code(), op.name(), op.url(), TZ, "en", op.phone(), "");
    }
    return sb.toString();
  }

  private static String calendar() {
    var sb = new StringBuilder("service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date\n");
    for (var c : CALENDARS) {
      row(sb, c.serviceId(), flag(c.days(), MONDAY), flag(c.days(), TUESDAY), flag(c.days(), WEDNESDAY),
        flag(c.days(), THURSDAY), flag(c.days(), FRIDAY), flag(c.days(), SATURDAY), flag(c.days(), SUNDAY),
        compact(c.start()), compact(c.end()));
    }
    return sb.toString();
  }

  private static String calendarDates() {
    var sb = new StringBuilder("service_id,date,exception_type\n");
    for (var e : CALENDAR_EXCEPTIONS) {
      row(sb, e.serviceId(), compact(e.date()), e.type());
    }
    return sb.toString();
  }

  private static String feedInfo() {
    var sb = new StringBuilder("feed_publisher_name,feed_publisher_url,feed_lang,feed_start_date,feed_end_date,feed_version\n");
    row(sb, "velociRAPTOR sample feed", "https://github.com/joshuaharwood/velociRAPTOR", "en", compact(FEED_START), compact(FEED_END), "sample-1");
    return sb.toString();
  }

  private static String linksFile() {
    var sb = new StringBuilder("from_stop_id,to_stop_id,mode,duration,start_time,end_time,start_date,end_date,monday,tuesday,wednesday,thursday,friday,saturday,sunday\n");
    for (var l : LINKS) {
      row(sb, l.from(), l.to(), l.mode(), l.duration(), time(l.start()), time(l.end()), "2026-01-01", "2026-12-31",
        flag(l.days(), MONDAY), flag(l.days(), TUESDAY), flag(l.days(), WEDNESDAY), flag(l.days(), THURSDAY),
        flag(l.days(), FRIDAY), flag(l.days(), SATURDAY), flag(l.days(), SUNDAY));
    }
    return sb.toString();
  }

  private static String routes() {
    var sb = new StringBuilder("route_id,agency_id,route_short_name,route_long_name,route_type,route_text_color,route_color,route_url,route_desc\n");
    var seen = new java.util.HashSet<String>();
    for (var p : PATTERNS) {
      if (seen.add(p.routeId())) {
        row(sb, p.routeId(), p.operator().code(), p.shortName(), p.longName(), 2, "", "", "", p.desc());
      }
    }
    return sb.toString();
  }

  private static String stopTimes() {
    var sb = new StringBuilder("trip_id,arrival_time,departure_time,stop_id,stop_sequence,stop_headsign,pickup_type,drop_off_type,shape_dist_traveled,timepoint\n");
    for (var t : trips()) {
      int seq = 1;
      for (var c : t.calls()) {
        row(sb, t.id(), time(c.arrival()), time(c.departure()), c.crs(), seq++, c.platform(), c.pickup(), c.dropOff(), "", 1);
      }
    }
    return sb.toString();
  }

  private static String stops() {
    var sb = new StringBuilder("stop_id,stop_code,stop_name,stop_desc,stop_lat,stop_lon,zone_id,stop_url,location_type,parent_station,stop_timezone,wheelchair_boarding\n");
    for (var s : STATIONS) {
      row(sb, s.crs(), s.tiploc(), s.name(), s.cate(), s.lat(), s.lon(), "", "", 0, "", TZ, 0);
    }
    return sb.toString();
  }

  private static String transfers() {
    var sb = new StringBuilder("from_stop_id,to_stop_id,transfer_type,min_transfer_time\n");
    for (var s : STATIONS) {
      if (s.hasInterchangeRow()) {
        row(sb, s.crs(), s.crs(), 2, s.interchange());
      }
    }
    return sb.toString();
  }

  private static String tripsFile() {
    var sb = new StringBuilder("route_id,service_id,trip_id,trip_headsign,trip_short_name,direction_id,wheelchair_accessible,bikes_allowed\n");
    for (var t : trips()) {
      row(sb, t.pattern().routeId(), t.serviceId(), t.id(), t.uid(), t.headcode(), 0, 0, 0);
    }
    return sb.toString();
  }

  // --- formatting -------------------------------------------------------------------------------------------

  private static void row(StringBuilder sb, Object... fields) {
    sb.append(Arrays.stream(fields).map(SampleFeed::csv).collect(Collectors.joining(","))).append('\n');
  }

  private static String csv(Object value) {
    String s = String.valueOf(value);
    return s.contains(",") || s.contains("\"") ? '"' + s.replace("\"", "\"\"") + '"' : s;
  }

  private static String flag(Set<DayOfWeek> days, DayOfWeek day) {
    return days.contains(day) ? "1" : "0";
  }

  private static String compact(LocalDate date) {
    return String.format("%04d%02d%02d", date.getYear(), date.getMonthValue(), date.getDayOfMonth());
  }

  /** GTFS time, running past 24:00:00 for calls after midnight on the service day. */
  static String time(int seconds) {
    return String.format("%02d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
  }

  private static String hhmm(int seconds) {
    return String.format("%02d%02d", seconds / 3600, (seconds / 60) % 60);
  }

  static int hm(int hours, int minutes) {
    return hours * 3600 + minutes * 60;
  }

  private static int m(int minutes) {
    return minutes * 60;
  }
}
