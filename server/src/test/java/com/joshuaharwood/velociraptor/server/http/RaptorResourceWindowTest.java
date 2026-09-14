package com.joshuaharwood.velociraptor.server.http;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit test (no Quarkus/S3) for the query-window translation. The resource expresses the
 * window in seconds from the <em>start</em> service date's midnight, so an {@code endDate} on the
 * following day exceeds 86400 and the query includes GTFS &gt;24h (after-midnight) departures. The
 * previous {@code endDate.toLocalTime().toSecondOfDay()} capped it at 86399 and broke cross-midnight
 * windows.
 */
class RaptorResourceWindowTest {

  private static final LocalDate SERVICE_DATE = LocalDate.of(2025, 6, 7); // a Saturday

  @Test
  void startTimeIsSecondsSinceServiceMidnight() {
    var start = LocalDateTime.of(2025, 6, 7, 23, 30); // 23:30 on the service date
    assertEquals(84_600, RaptorResource.secondsSinceServiceMidnight(SERVICE_DATE, start));
  }

  @Test
  void startOfDayIsZero() {
    assertEquals(0, RaptorResource.secondsSinceServiceMidnight(SERVICE_DATE, SERVICE_DATE.atStartOfDay()));
  }

  @Test
  void sameDayEndStaysWithinTheDay() {
    var end = LocalDateTime.of(2025, 6, 7, 23, 0); // 23:00 same day
    assertEquals(82_800, RaptorResource.secondsSinceServiceMidnight(SERVICE_DATE, end));
  }

  @Test
  void nextDayEndExceeds86400() {
    // 01:00 the following morning -> 25:00 in service-day seconds. This is the fix: the old
    // toSecondOfDay() would have returned 3600 and collapsed the window.
    var end = LocalDateTime.of(2025, 6, 8, 1, 0);
    assertEquals(90_000, RaptorResource.secondsSinceServiceMidnight(SERVICE_DATE, end));
  }

  @Test
  void endAtFourAmNextDayReachesTwentyEightHundred() {
    // Trains running "until ~4am" -> 28:00 in service-day seconds.
    var end = LocalDateTime.of(2025, 6, 8, 4, 0);
    assertEquals(100_800, RaptorResource.secondsSinceServiceMidnight(SERVICE_DATE, end));
  }

  @Test
  void midnightNextDayIsExactly86400() {
    var end = LocalDateTime.of(2025, 6, 8, 0, 0);
    assertEquals(86_400, RaptorResource.secondsSinceServiceMidnight(SERVICE_DATE, end));
  }

  @Test
  void crossMidnightWindowKeepsStartBeforeEnd() {
    // The whole point: a Sat 23:30 -> Sun 01:00 window stays ordered (84600 < 90000) instead of
    // collapsing to [84600, 3600) and matching nothing.
    int start = RaptorResource.secondsSinceServiceMidnight(SERVICE_DATE, LocalDateTime.of(2025, 6, 7, 23, 30));
    int end = RaptorResource.secondsSinceServiceMidnight(SERVICE_DATE, LocalDateTime.of(2025, 6, 8, 1, 0));
    assertTrue(start < end, "cross-midnight window must stay ordered");
  }
}
