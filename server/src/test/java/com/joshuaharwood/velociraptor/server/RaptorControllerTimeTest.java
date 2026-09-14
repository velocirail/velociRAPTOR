package com.joshuaharwood.velociraptor.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

/**
 * Verifies both wire-boundary time converters resolve GTFS local wall-clock seconds to the correct
 * Europe/London offset across BST, GMT, and both DST-change days. Plain unit test - no Quarkus/S3.
 *
 * <p>Both serialisation paths are checked in every case: {@link RaptorController#toOffset} (the
 * {@code GET /} / SimpleJourney path, from {@code (date, seconds)}) and {@link RaptorController#atLondon}
 * (the {@code /detail} + {@code /first-arrival} / RailJourney path, from the rail module's wall-clock
 * {@code LocalDateTime}). They must agree.
 *
 * <p>UK 2025 DST: clocks spring forward Sun 30 Mar (01:00 GMT -> 02:00 BST) and fall back Sun 26
 * Oct (02:00 BST -> 01:00 GMT).
 */
class RaptorControllerTimeTest {

  // Asserts the expected offset date-time is produced by BOTH converters for the given local time.
  private static OffsetDateTime offset(String date, int hour, int minute) {
    LocalDate localDate = LocalDate.parse(date);
    int seconds = (hour * 60 + minute) * 60;
    OffsetDateTime viaToOffset = RaptorController.toOffset(localDate, seconds);
    // The rail path feeds atLondon the wall-clock LocalDateTime the rail module builds, i.e.
    // midnight + seconds (LocalDateTime.of(date, MIDNIGHT).plusSeconds), incl. GTFS >24h rollover.
    OffsetDateTime viaAtLondon = RaptorController.atLondon(localDate.atStartOfDay().plusSeconds(seconds));
    assertEquals(viaToOffset, viaAtLondon, "toOffset and atLondon must agree (GET / vs /detail path)");
    return viaToOffset;
  }

  @Test
  void summerTimesCarryBstOffset() {
    assertEquals(OffsetDateTime.parse("2025-06-01T21:54:00+01:00"), offset("2025-06-01", 21, 54));
  }

  @Test
  void winterTimesCarryGmtOffset() {
    assertEquals(OffsetDateTime.parse("2025-12-07T21:54:00Z"), offset("2025-12-07", 21, 54));
  }

  @Test
  void afterMidnightTimesRollToNextCalendarDay() {
    // GTFS >24h: 25:30 on the 2025-06-01 service date is 01:30 the next morning, still BST.
    assertEquals(OffsetDateTime.parse("2025-06-02T01:30:00+01:00"), offset("2025-06-01", 25, 30));
  }

  @Test
  void springForwardBeforeTransitionIsGmt() {
    assertEquals(OffsetDateTime.parse("2025-03-30T00:30:00Z"), offset("2025-03-30", 0, 30));
  }

  @Test
  void springForwardAfterTransitionIsBst() {
    // The 10:00 service must read 10:00 local (BST), not 11:00 - the elapsed-seconds bug.
    assertEquals(OffsetDateTime.parse("2025-03-30T10:00:00+01:00"), offset("2025-03-30", 10, 0));
  }

  @Test
  void springForwardGapRollsToBst() {
    // 01:30 does not exist (clocks jump 01:00 -> 02:00); the zone rules shift it to 02:30 BST.
    assertEquals(OffsetDateTime.parse("2025-03-30T02:30:00+01:00"), offset("2025-03-30", 1, 30));
  }

  @Test
  void fallBackAmbiguousHourTakesEarlierBstOffset() {
    // 01:30 occurs twice on fall-back day; resolve to the earlier (BST) occurrence.
    assertEquals(OffsetDateTime.parse("2025-10-26T01:30:00+01:00"), offset("2025-10-26", 1, 30));
  }

  @Test
  void fallBackAfterTransitionIsGmt() {
    assertEquals(OffsetDateTime.parse("2025-10-26T02:30:00Z"), offset("2025-10-26", 2, 30));
  }
}
