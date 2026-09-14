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
import static org.assertj.core.api.Assertions.from;

/**
 * Fixed-link legs get times from the trains around them: after a train, they start at its arrival
 * plus the interchange at the link's origin; before the first train, they end an interchange before
 * it departs. The reference implementation left them null (BUGS.md 4.2).
 */
class RailJourneyFactoryFixedLinkTimesTest {

  private static final LocalDate START = LocalDate.of(2025, 6, 1);
  private static final Stop O = new Stop("O");
  private static final Stop Y = new Stop("Y");
  private static final Stop A = new Stop("A");
  private static final Stop B = new Stop("B");
  private static final Stop C = new Stop("C");

  private static RailTrip trip(String id, Stop from, int dep, Stop to, int arr) {
    return new RailTrip(id,
        List.of(new StopTime(from, dep, dep, true, false), new StopTime(to, arr, arr, false, true)),
        "svc", "agency", null);
  }

  private static LocalDateTime at(int seconds) {
    return START.atStartOfDay().plusSeconds(seconds);
  }

  @Test
  void aLinkAfterATrainStartsAtItsArrivalPlusTheOriginInterchange() {
    // A→B arrives 1600; link B→C takes 600 with a 300 s interchange at B: 1900 → 2500.
    Map<Stop, Map<Integer, ResultConnectionIndex>> kConnections = Map.of(
        B, Map.of(1, new ResultConnection(trip("t1", A, 1000, B, 1600), 0, 1)),
        C, Map.of(2, new TransferLeg(B, C, 600, 0, Integer.MAX_VALUE, 300, 120, "WALK")));

    var legs = new RailJourneyFactory(START).getResults(kConnections, C, null).getFirst().legs();

    assertThat((Leg.FixedLink) legs.getLast())
        .returns(at(1900), from(Leg.FixedLink::departureTime))
        .returns(at(2500), from(Leg.FixedLink::arrivalTime))
        .returns("WALK", from(Leg.FixedLink::mode));
  }

  @Test
  void aLeadingLinkEndsAnInterchangeBeforeTheFirstTrainDeparts() {
    // Link O→Y takes 300 with a 60 s interchange at Y; the train leaves Y at 1000: 640 → 940.
    Map<Stop, Map<Integer, ResultConnectionIndex>> kConnections = Map.of(
        Y, Map.of(1, new TransferLeg(O, Y, 300, 0, Integer.MAX_VALUE, 0, 60, "TUBE")),
        A, Map.of(2, new ResultConnection(trip("t2", Y, 1000, A, 1600), 0, 1)));

    var legs = new RailJourneyFactory(START).getResults(kConnections, A, null).getFirst().legs();

    assertThat((Leg.FixedLink) legs.getFirst())
        .returns(at(640), from(Leg.FixedLink::departureTime))
        .returns(at(940), from(Leg.FixedLink::arrivalTime));
    assertThat(legs.getLast()).isInstanceOf(Leg.RailLeg.class);
  }

  @Test
  void aLinkBetweenTwoTrainsUsesTheFirstTrainsArrival() {
    // A→B arrives 1600, link B→Y 600 with interchange 300 at B: 1900 → 2500; Y→C departs 3000.
    Map<Stop, Map<Integer, ResultConnectionIndex>> kConnections = Map.of(
        B, Map.of(1, new ResultConnection(trip("t1", A, 1000, B, 1600), 0, 1)),
        Y, Map.of(2, new TransferLeg(B, Y, 600, 0, Integer.MAX_VALUE, 300, 60, "TUBE")),
        C, Map.of(3, new ResultConnection(trip("t3", Y, 3000, C, 3600), 0, 1)));

    var legs = new RailJourneyFactory(START).getResults(kConnections, C, null).getFirst().legs();

    assertThat(legs).hasSize(3);
    assertThat((Leg.FixedLink) legs.get(1))
        .returns(at(1900), from(Leg.FixedLink::departureTime))
        .returns(at(2500), from(Leg.FixedLink::arrivalTime));
  }
}
