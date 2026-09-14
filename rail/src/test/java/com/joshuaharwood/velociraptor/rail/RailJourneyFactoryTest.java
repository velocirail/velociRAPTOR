package com.joshuaharwood.velociraptor.rail;

import com.joshuaharwood.velociraptor.raptor.model.Leg.TransferLeg;
import com.joshuaharwood.velociraptor.raptor.model.ResultConnectionIndex;
import com.joshuaharwood.velociraptor.raptor.model.ResultConnectionIndex.ResultConnection;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.model.StopTime;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code searchTime} lower bound is carried as seconds-since-midnight and converted with the
 * same {@code LocalDateTime} arithmetic as the leg times, so GTFS &gt;24h (after-midnight)
 * departures are bounded on the correct day rather than wrapped back into the start date.
 */
class RailJourneyFactoryTest {

  private static final LocalDate START = LocalDate.of(2025, 6, 1);
  private static final Stop A = new Stop("A");
  private static final Stop B = new Stop("B");

  private static final int H25 = 90_000;    // 25:00:00 -> 2025-06-02T01:00
  private static final int H25_30 = 91_800; // 25:30:00 -> 2025-06-02T01:30

  /** A single rail leg A(depart) -> B(arrive), reachable at round 1. */
  private static Map<Stop, Map<Integer, ResultConnectionIndex>> oneLeg(int departA, int arriveB) {
    var trip = new RailTrip("t1",
        java.util.List.of(
            new StopTime(A, departA, departA, true, false),
            new StopTime(B, arriveB, arriveB, false, true)),
        "svc", "agency", null);
    return Map.of(B, Map.of(1, new ResultConnection(trip, 0, 1)));
  }

  @Test
  void afterMidnightDepartureAtOrAfterBoundIsKept() {
    var factory = new RailJourneyFactory(START);
    // Leg departs 25:00; lower bound 25:00 -> kept (boundary).
    var results = factory.getResults(oneLeg(H25, H25_30), B, H25);

    assertThat(results).hasSize(1);
    var firstLeg = (Leg.RailLeg) results.getFirst().legs().getFirst();
    // The departure rolls into the next calendar day rather than wrapping to 01:00 on START.
    assertThat(firstLeg.departureTime()).isEqualTo(LocalDateTime.of(2025, 6, 2, 1, 0));
  }


  @Test
  void nullBoundKeepsEverything() {
    var factory = new RailJourneyFactory(START);
    var results = factory.getResults(oneLeg(H25, H25_30), B, null);

    assertThat(results).hasSize(1);
  }

  private static final Stop C = new Stop("C");
  private static final Stop D = new Stop("D");

  private static RailTrip railTrip(Stop from, int departFrom, Stop to, int arriveTo) {
    return new RailTrip("trip-" + from.id() + to.id(),
        List.of(new StopTime(from, departFrom, departFrom, true, false),
                new StopTime(to, arriveTo, arriveTo, false, true)),
        "svc", "agency", null);
  }

  private LocalDateTime at(int seconds) {
    return LocalDateTime.of(START, java.time.LocalTime.MIDNIGHT).plusSeconds(seconds);
  }

  /**
   * Regression: in range mode {@code ScanResults.addRangeRound} copies a stop's arrival forward into
   * later rounds (rRAPTOR label retention) without re-recording its connection, and the origin seed
   * never has a connection at all. So the destination's parent leg can be stored at a round whose
   * boarding stop is the origin seed - which has <em>no</em> entry in {@code kConnections}. The
   * back-walk used to index {@code kConnections.get(previousOrigin).get(i)} blindly and threw an NPE
   * (HTTP 500 on {@code /detail}). The walk must instead terminate cleanly at the origin seed.
   */
  @Test
  void reconstructsWhenParentLegIsStoredAtAHigherRoundThanItsLegCount() {
    // D reached at round 2 via a single trip A -> D, but A (the origin seed) has no connection entry.
    var tripAD = railTrip(A, 1_000, D, 2_000);
    Map<Stop, Map<Integer, ResultConnectionIndex>> kConnections =
        Map.of(D, Map.of(2, new ResultConnection(tripAD, 0, 1)));

    var results = new RailJourneyFactory(START).getResults(kConnections, D, null);

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().legs()).hasSize(1);
    var leg = (Leg.RailLeg) results.getFirst().legs().getFirst();
    assertThat(leg.origin()).isEqualTo(A);
    assertThat(leg.destination()).isEqualTo(D);
  }

  /**
   * Regression: when an intermediate boarding stop's arrival is carried forward (its connection lives
   * at an earlier round than {@code i - 1}), the back-walk must follow it down to its actual round and
   * rebuild the full journey rather than truncating or throwing.
   */
  @Test
  void followsACarriedForwardParentDownToItsActualRound() {
    // D reached at round 3 via C -> D; C's own connection (A -> C) was recorded back at round 1.
    var tripCD = railTrip(C, 3_000, D, 4_000);
    var tripAC = railTrip(A, 1_000, C, 2_000);
    Map<Stop, Map<Integer, ResultConnectionIndex>> kConnections = Map.of(
        D, Map.of(3, new ResultConnection(tripCD, 0, 1)),
        C, Map.of(1, new ResultConnection(tripAC, 0, 1)));

    var results = new RailJourneyFactory(START).getResults(kConnections, D, null);

    assertThat(results).hasSize(1);
    var legs = results.getFirst().legs();
    assertThat(legs).hasSize(2);
    assertThat(((Leg.RailLeg) legs.get(0)).origin()).isEqualTo(A);
    assertThat(((Leg.RailLeg) legs.get(0)).destination()).isEqualTo(C);
    assertThat(((Leg.RailLeg) legs.get(1)).origin()).isEqualTo(C);
    assertThat(((Leg.RailLeg) legs.get(1)).destination()).isEqualTo(D);
  }

}
