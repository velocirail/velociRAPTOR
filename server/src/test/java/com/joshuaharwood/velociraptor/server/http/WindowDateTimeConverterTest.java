package com.joshuaharwood.velociraptor.server.http;

import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class WindowDateTimeConverterTest {

  private final WindowDateTimeParamConverterProvider.WindowDateTimeConverter converter =
      new WindowDateTimeParamConverterProvider.WindowDateTimeConverter("startDate");

  @Test
  void offsetInputIsTakenAsGiven() {
    assertEquals(OffsetDateTime.parse("2026-09-08T07:45:00Z"), converter.fromString("2026-09-08T07:45:00Z"));
    assertEquals(OffsetDateTime.parse("2026-09-08T08:45:00+01:00"), converter.fromString("2026-09-08T08:45:00+01:00"));
  }

  @Test
  void offsetInputWithFractionalSecondsIsAccepted() {
    assertEquals(OffsetDateTime.parse("2026-09-08T07:45:00Z"), converter.fromString("2026-09-08T07:45:00.000Z"));
  }

  @Test
  void utcWindowDuringBstIsAnHourLaterOnTheRailClock() {
    // 07:45Z on a September day is 08:45 British Summer Time: the window a 09:00 search from the portal opens with.
    assertEquals(LocalDateTime.of(2026, 9, 8, 8, 45), RaptorResource.railTime(converter.fromString("2026-09-08T07:45:00Z")));
  }

  @Test
  void zoneLessInputIsReadAsLondonWallClock() {
    assertEquals(OffsetDateTime.parse("2026-09-08T08:45:00+01:00"), converter.fromString("2026-09-08T08:45:00"));
    assertEquals(OffsetDateTime.parse("2026-01-08T08:45:00Z"), converter.fromString("2026-01-08T08:45:00"));
    // and lands on the rail clock unchanged, exactly as the old LocalDateTime parameter did
    assertEquals(LocalDateTime.of(2026, 9, 8, 8, 45), RaptorResource.railTime(converter.fromString("2026-09-08T08:45:00")));
  }

  @Test
  void zoneLessInputInTheSpringForwardGapMovesForward() {
    // 01:30 does not exist on 2026-03-29; java.time resolves it to 02:30 BST.
    assertEquals(OffsetDateTime.parse("2026-03-29T02:30:00+01:00"), converter.fromString("2026-03-29T01:30:00"));
  }

  @Test
  void anythingElseIsA400NamingTheParameter() {
    var problem = assertThrows(BadRequestException.class, () -> converter.fromString("2026-09-08 08:45"));
    assertTrue(problem.getMessage().startsWith("startDate=2026-09-08 08:45 "), problem.getMessage());
    // a zoned form is not ISO 8601 and is refused too
    assertThrows(BadRequestException.class, () -> converter.fromString("2026-09-08T08:45:00+01:00[Europe/London]"));
  }

  @Test
  void blankIsNullSoBeanValidationReportsTheMissingParameter() {
    assertNull(converter.fromString(null));
    assertNull(converter.fromString(""));
  }

  @Test
  void toStringRoundTrips() {
    assertEquals("2026-09-08T08:45+01:00", converter.toString(OffsetDateTime.parse("2026-09-08T08:45:00+01:00")));
    assertNull(converter.toString(null));
  }
}
