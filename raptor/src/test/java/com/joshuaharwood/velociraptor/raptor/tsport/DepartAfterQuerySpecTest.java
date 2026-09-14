package com.joshuaharwood.velociraptor.raptor.tsport;

import com.joshuaharwood.velociraptor.raptor.TestRaptorBuilder;
import org.junit.jupiter.api.Test;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.model.Transfer;
import com.joshuaharwood.velociraptor.raptor.model.Trip;
import com.joshuaharwood.velociraptor.raptor.query.DepartAfterQuery;
import com.joshuaharwood.velociraptor.raptor.result.Journey;
import com.joshuaharwood.velociraptor.raptor.result.JourneyFactory;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static com.joshuaharwood.velociraptor.raptor.TestData.j;
import static com.joshuaharwood.velociraptor.raptor.TestData.st;
import static com.joshuaharwood.velociraptor.raptor.TestData.stop;
import static com.joshuaharwood.velociraptor.raptor.TestData.stripTrips;
import static com.joshuaharwood.velociraptor.raptor.TestData.t;
import static com.joshuaharwood.velociraptor.raptor.TestData.tfi;

class DepartAfterQuerySpecTest {

  private static final LocalDate TEST_DATE = LocalDate.of(2018, 10, 16);

  private List<Journey> planDepartAfter(List<Trip> trips,
                                        Map<Stop, List<Transfer>> transfers,
                                        Map<Stop, Integer> interchange,
                                        String origin, String destination,
                                        int time) {
    var raptor = TestRaptorBuilder.create(trips, transfers, interchange);
    var factory = new JourneyFactory();
    var query = new DepartAfterQuery(raptor, factory);
    return query.plan(stop(origin), stop(destination), TEST_DATE, time);
  }

  private List<Journey> planDepartAfter(List<Trip> trips,
                                        String origin, String destination,
                                        int time) {
    return planDepartAfter(trips, Map.of(), new HashMap<>(), origin, destination, time);
  }

  @Test
  void findsJourneysWithDirectConnections() {
    var trips = List.of(
      t(
        st("A", null, 1000),
        st("B", 1030, 1035),
        st("C", 1100, null)
      )
    );

    var result = stripTrips(planDepartAfter(trips, "A", "C", 900));

    assertThat(result).containsExactly(
      j(List.of(
        st("A", null, 1000),
        st("B", 1030, 1035),
        st("C", 1100, null)
      ))
    );
  }

  @Test
  void findsTheEarliestJourney() {
    var trips = List.of(
      t(
        st("A", null, 1400),
        st("B", 1430, 1435),
        st("C", 1500, null)
      ),
      t(
        st("A", null, 1000),
        st("B", 1030, 1035),
        st("C", 1100, null)
      )
    );

    var result = stripTrips(planDepartAfter(trips, "A", "C", 900));

    assertThat(result).containsExactly(
      j(List.of(
        st("A", null, 1000),
        st("B", 1030, 1035),
        st("C", 1100, null)
      ))
    );
  }

  @Test
  void findsJourneysWithASingleConnection() {
    var trips = List.of(
      t(
        st("A", null, 1000),
        st("B", 1030, 1035),
        st("C", 1100, null)
      ),
      t(
        st("D", null, 1000),
        st("B", 1030, 1035),
        st("E", 1100, null)
      )
    );

    var result = stripTrips(planDepartAfter(trips, "A", "E", 900));

    assertThat(result).containsExactly(
      j(
        List.of(st("A", null, 1000), st("B", 1030, 1035)),
        List.of(st("B", 1030, 1035), st("E", 1100, null))
      )
    );
  }

  @Test
  void doesNotReturnJourneysThatCannotBeMade() {
    var trips = List.of(
      t(
        st("A", null, 1000),
        st("B", 1035, 1035),
        st("C", 1100, null)
      ),
      t(
        st("D", null, 1000),
        st("B", 1030, 1030),
        st("E", 1100, null)
      )
    );

    var result = planDepartAfter(trips, "A", "E", 900);

    assertThat(result).isEmpty();
  }

  @Test
  void returnsTheFastestAndTheLeastChanges() {
    var trips = List.of(
      t(
        st("A", null, 1000),
        st("B", 1030, 1030),
        st("C", 1200, null)
      ),
      t(
        st("B", null, 1030),
        st("C", 1100, null)
      )
    );

    var result = stripTrips(planDepartAfter(trips, "A", "C", 900));

    var direct = j(List.of(
      st("A", null, 1000),
      st("B", 1030, 1030),
      st("C", 1200, null)
    ));

    var change = j(
      List.of(st("A", null, 1000), st("B", 1030, 1030)),
      List.of(st("B", null, 1030), st("C", 1100, null))
    );

    assertThat(result).containsExactly(direct, change);
  }

  @Test
  void choosesTheFastestJourneyWhereNumberOfLegsIsSame() {
    var trips = List.of(
      t(
        st("A", null, 1000),
        st("B", 1030, 1030),
        st("C", 1100, null)
      ),
      t(
        st("C", null, 1200),
        st("D", 1230, 1230),
        st("E", 1300, null)
      ),
      t(
        st("A", null, 1100),
        st("F", 1130, 1130),
        st("G", 1200, null)
      ),
      t(
        st("G", null, 1200),
        st("H", 1230, 1230),
        st("E", 1255, null)
      )
    );

    var result = stripTrips(planDepartAfter(trips, "A", "E", 900));

    var fastest = j(
      List.of(st("A", null, 1100), st("F", 1130, 1130), st("G", 1200, null)),
      List.of(st("G", null, 1200), st("H", 1230, 1230), st("E", 1255, null))
    );

    assertThat(result).containsExactly(fastest);
  }

  @Test
  void choosesAnArbitraryJourneyWhenTheyAreTheSame() {
    var trips = List.of(
      t(
        st("A", null, 1000),
        st("B", 1030, 1030),
        st("C", 1100, null)
      ),
      t(
        st("C", null, 1200),
        st("D", 1230, 1230),
        st("E", 1300, null)
      ),
      t(
        st("A", null, 1100),
        st("F", 1130, 1130),
        st("G", 1200, null)
      ),
      t(
        st("G", null, 1200),
        st("H", 1230, 1230),
        st("E", 1300, null)
      )
    );

    var result = stripTrips(planDepartAfter(trips, "A", "E", 900));

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().legs()).hasSize(2);
    assertThat(result.getFirst().arrivalTime()).isEqualTo(1300);
  }

  @Test
  void choosesTheCorrectChangePoint() {
    var trips = List.of(
      t(
        st("A", null, 1000),
        st("B", 1030, null)
      ),
      t(
        st("A", null, 1030),
        st("C", 1200, null)
      ),
      t(
        st("C", null, 1000),
        st("B", 1030, 1030),
        st("E", 1100, null)
      ),
      t(
        st("C", null, 1200),
        st("B", 1230, 1230),
        st("E", 1300, null)
      )
    );

    var result = stripTrips(planDepartAfter(trips, "A", "E", 900));

    var change = j(
      List.of(st("A", null, 1000), st("B", 1030, null)),
      List.of(st("B", 1030, 1030), st("E", 1100, null))
    );

    assertThat(result).containsExactly(change);
  }

  @Test
  void findsJourneysWithATransfer() {
    var trips = List.of(
      t(
        st("A", null, 1000),
        st("B", 1030, 1035),
        st("C", 1100, null)
      ),
      t(
        st("D", null, 1200),
        st("E", 1300, null)
      )
    );

    var transfers = Map.of(stop("C"), List.of(transfer("C", "D", 10)));

    var result = stripTrips(planDepartAfter(trips, transfers, new HashMap<>(),
      "A", "E", 900));

    assertThat(result).containsExactly(
      j(
        List.of(st("A", null, 1000), st("B", 1030, 1035), st("C", 1100, null)),
        tfi("C", "D", 10, 0, 0),
        List.of(st("D", null, 1200), st("E", 1300, null))
      )
    );
  }

  @Test
  void usesATransferIfItIsFaster() {
    var trips = List.of(
      t(
        st("A", null, 1000),
        st("B", 1030, 1030),
        st("C", 1100, null)
      ),
      t(
        st("C", null, 1130),
        st("D", 1200, null)
      )
    );

    var transfers = Map.of(stop("C"), List.of(transfer("C", "D", 10)));

    var result = stripTrips(planDepartAfter(trips, transfers, new HashMap<>(),
      "A", "D", 900));

    var transfer = j(
      List.of(st("A", null, 1000), st("B", 1030, 1030), st("C", 1100, null)),
      tfi("C", "D", 10, 0, 0)
    );

    assertThat(result).containsExactly(transfer);
  }

  @Test
  void doesNotAllowPickUpFromLocationsWithoutPickup() {
    var trips = List.of(
      t(
        st("A", null, 1000),
        st("B", 1030, 1030),
        st("C", 1200, null)
      ),
      t(
        st("E", null, 1000),
        st("B", 1030, null),
        st("C", 1100, null)
      )
    );

    var result = stripTrips(planDepartAfter(trips, "A", "C", 900));

    var direct = j(List.of(
      st("A", null, 1000),
      st("B", 1030, 1030),
      st("C", 1200, null)
    ));

    assertThat(result).containsExactly(direct);
  }

  @Test
  void doesNotAllowDropOffAtNonDropOffLocations() {
    var trips = List.of(
      t(
        st("A", null, 1000),
        st("B", null, 1030),
        st("C", 1200, null)
      ),
      t(
        st("E", null, 1000),
        st("B", 1030, 1030),
        st("C", 1100, null)
      )
    );

    var result = stripTrips(planDepartAfter(trips, "A", "C", 900));

    var direct = j(List.of(
      st("A", null, 1000),
      st("B", null, 1030),
      st("C", 1200, null)
    ));

    assertThat(result).containsExactly(direct);
  }

  @Test
  void appliesInterchangeTimes() {
    var trips = List.of(
      t(
        st("A", null, 1000),
        st("B", 1030, 1030),
        st("C", 1200, null)
      ),
      t(
        st("B", null, 1030),
        st("C", 1100, null)
      ),
      t(
        st("B", null, 1040),
        st("C", 1110, null)
      )
    );

    var interchange = new HashMap<Stop, Integer>();
    interchange.put(stop("B"), 10);

    var result = stripTrips(planDepartAfter(trips, Map.of(), interchange,
      "A", "C", 900));

    var direct = j(List.of(
      st("A", null, 1000),
      st("B", 1030, 1030),
      st("C", 1200, null)
    ));

    var change = j(
      List.of(st("A", null, 1000), st("B", 1030, 1030)),
      List.of(st("B", null, 1040), st("C", 1110, null))
    );

    assertThat(result).containsExactly(direct, change);
  }

  @Test
  void appliesInterchangeTimesToTransfers() {
    var trips = List.of(
      t(
        st("A", null, 1000),
        st("B", 1030, null)
      ),
      t(st("C", null, 1030), st("D", 1100, null)),
      t(st("C", null, 1042), st("D", 1142, null)),
      t(st("C", null, 1047), st("D", 1147, null)),
      t(st("C", null, 1049), st("D", 1149, null)),
      t(st("C", null, 1052), st("D", 1152, null)),
      t(st("C", null, 1054), st("D", 1154, null)),
      t(st("C", null, 1056), st("D", 1156, null)),
      t(st("C", null, 1200), st("D", 1300, null))
    );

    var transfers = Map.of(stop("B"), List.of(transfer("B", "C", 11)));

    var interchange = new HashMap<Stop, Integer>();
    interchange.put(stop("B"), 5);
    interchange.put(stop("C"), 7);

    var result = stripTrips(planDepartAfter(trips, transfers, interchange,
      "A", "D", 900));

    // The B->C fixed link sums interchange at both ends. Entry interchange(B)=5
    // is charged off the train at B (a genuine interchange, round >= 2): the link leaves B at
    // 1030 + 5 = 1035, arrives C at 1046; the exit interchange(C)=7 is charged as board-slack, so
    // boarding needs 1046 + 7 = 1053, catching the 1054 departure. (The leg also records both
    // stops' interchange times as metadata.)
    var lastPossible = j(
      List.of(st("A", null, 1000), st("B", 1030, null)),
      tfi("B", "C", 11, 5, 7),
      List.of(st("C", null, 1054), st("D", 1154, null))
    );

    assertThat(result).containsExactly(lastPossible);
  }

  @Test
  void chargesEntryInterchangeWhenAlightingToAFixedLink() {
    // Alighting off a train at X and taking a fixed link X->D is a genuine
    // interchange, so the link's cost sums interchange(X) at the entry. The train arrives X at 1000;
    // the X->D link leaves at 1000 + interchange(X)=10 = 1010 and arrives D at 1012, not 1002. The
    // slower direct train (arr 1020) keeps D on the network and is a valid fewer-changes option.
    var trips = List.of(
      t(st("A", null, 900), st("X", 1000, null)),
      t(st("A", null, 900), st("D", 1020, null))
    );

    var transfers = Map.of(stop("X"), List.of(transfer("X", "D", 2)));

    var interchange = new HashMap<Stop, Integer>();
    interchange.put(stop("X"), 10);

    var result = stripTrips(planDepartAfter(trips, transfers, interchange, "A", "D", 800));

    var viaTransfer = j(
      List.of(st("A", null, 900), st("X", 1000, null)),
      tfi("X", "D", 2, 10, 0)
    );
    var direct = j(List.of(st("A", null, 900), st("D", 1020, null)));

    assertThat(result).containsExactlyInAnyOrder(direct, viaTransfer);
    assertThat(viaTransfer.arrivalTime()).isEqualTo(1012);
  }

  @Test
  void doesNotChargeEntryInterchangeWhenAFixedLinkLeavesTheOrigin() {
    // The entry interchange applies only to a fixed link taken as a genuine interchange off a
    // service (round >= 2). A link leaving the origin seed (round 1) pays no entry interchange:
    // it starts at the journey's departure with no interchange prepended at the origin. Here A->B (interchange(A)=10) is seeded at 900 and arrives
    // B at 902 (not 912), so the 910 train at B is catchable. The link's *board* interchange at B
    // (interchange(B)=5) is still backed off the reported departure: the latest feasible departure is
    // 910 - 5 - 2 = 903 (leave A 903, walk to B 905, +5 board-slack = 910). The slow direct A->C
    // train keeps A on the network.
    var trips = List.of(
      t(st("A", null, 900), st("C", 1100, null)),
      t(st("B", null, 910), st("C", 1000, null))
    );

    var transfers = Map.of(stop("A"), List.of(transfer("A", "B", 2)));

    var interchange = new HashMap<Stop, Integer>();
    interchange.put(stop("A"), 10);
    interchange.put(stop("B"), 5);

    var result = stripTrips(planDepartAfter(trips, transfers, interchange, "A", "C", 900));

    var viaTransfer = j(
      tfi("A", "B", 2, 10, 5),
      List.of(st("B", null, 910), st("C", 1000, null))
    );
    var direct = j(List.of(st("A", null, 900), st("C", 1100, null)));

    assertThat(result).containsExactlyInAnyOrder(direct, viaTransfer);
    assertThat(viaTransfer.departureTime())
      .as("leading link backs off its board interchange: 910 - 5 - 2")
      .isEqualTo(903);
  }

  @Test
  void fixedLinkChargesInterchangeAtBothEnds() {
    // A fixed link sums the minimum interchange at both of its ends: arriving off a service charges
    // an entry interchange, and the onward departure charges an exit interchange. Worked through:
    // a TRAIN into Bond Street, an UNDERGROUND fixed link BDS->LBG, then a TRAIN out of London
    // Bridge, charging interchange at *both* ends of the link:
    //
    //     TRAIN  PAD 10:37 -> BDS 10:40
    //     [ 5m interchange at BDS, the fixed-link ENTRY ]
    //     TUBE   BDS 10:45 -> LBG 11:05            (20m link transit)
    //     [10m interchange at LBG, the fixed-link EXIT ]
    //     TRAIN  LBG 11:15 -> BTN 12:18
    //
    // i.e. train-arrival BDS 10:40 -> train-departure LBG 11:15 = 5 + 20 + 10 = 35m. We realise the
    // EXIT interchange as board-slack on the onward train; the ENTRY interchange is charged in
    // scanTransfers when the link is a genuine interchange off a service (round >= 2).
    //
    // Synthetic mirror of that middle segment, sized so the entry interchange changes the
    // result: P(rain in) -> B(DS) --link--> L(BG) -> Z(=BTN). interchange(B)=5, interchange(L)=10.
    var trips = List.of(
      t(st("P", null, 1000), st("B", 1040, null)),   // train into the fixed-link entry
      t(st("L", null, 1070), st("Z", 1100, null)),   // earlier train out of LBG (TrainA)
      t(st("L", null, 1080), st("Z", 1115, null))    // later train out of LBG   (TrainB)
    );

    var transfers = Map.of(stop("B"), List.of(transfer("B", "L", 20)));

    var interchange = new HashMap<Stop, Integer>();
    interchange.put(stop("B"), 5);
    interchange.put(stop("L"), 10);

    var result = stripTrips(planDepartAfter(trips, transfers, interchange, "P", "Z", 900));

    // Arrive B 1040; link entry charges interchange(B)=5 -> leaves B 1045, arrives L 1065;
    // board-slack interchange(L)=10 -> ready 1075, MISSES TrainA (1070) and catches TrainB (1080),
    // arriving Z at 1115. (Without the entry interchange we would wrongly
    // catch TrainA at 1070 and arrive 1100.)
    var perSpec = j(
      List.of(st("P", null, 1000), st("B", 1040, null)),
      tfi("B", "L", 20, 5, 10),
      List.of(st("L", null, 1080), st("Z", 1115, null))
    );
    assertThat(result).containsExactly(perSpec);
  }

  @Test
  void onlyUsesTripsCallerSupplies() {
    // With calendar filtering pushed upstream, callers only pass trips running on the query day.
    // Raptor should only route through trips it was handed.
    var activeTrips = List.of(
      t(st("A", null, 1000), st("B", 1030, null)),
      t(st("B", null, 1040), st("C", 1110, null))
    );

    var result = stripTrips(planDepartAfter(activeTrips, "A", "C", 900));

    var change = j(
      List.of(st("A", null, 1000), st("B", 1030, null)),
      List.of(st("B", null, 1040), st("C", 1110, null))
    );

    assertThat(result).containsExactly(change);
  }

  @Test
  void findsJourneysAfterGapsInRounds() {
    var trips = List.of(
      t(
        st("A", null, 1000),
        st("B", 1030, 1035),
        st("C", 1400, null)
      ),
      t(
        st("B", null, 1035),
        st("D", 1100, null)
      ),
      t(
        st("D", null, 1100),
        st("E", 1130, null)
      ),
      t(
        st("E", null, 1130),
        st("C", 1200, null)
      ),
      t(
        st("A", null, 1000),
        st("E", 1135, null)
      ),
      t(
        st("E", null, 1135),
        st("C", 1330, null)
      )
    );

    var result = stripTrips(planDepartAfter(trips, "A", "C", 900));

    var direct = j(List.of(
      st("A", null, 1000),
      st("B", 1030, 1035),
      st("C", 1400, null)
    ));

    var slowChange = j(
      List.of(st("A", null, 1000), st("E", 1135, null)),
      List.of(st("E", null, 1135), st("C", 1330, null))
    );

    var change = j(
      List.of(st("A", null, 1000), st("B", 1030, 1035)),
      List.of(st("B", null, 1035), st("D", 1100, null)),
      List.of(st("D", null, 1100), st("E", 1130, null)),
      List.of(st("E", null, 1130), st("C", 1200, null))
    );

    assertThat(result).containsExactlyInAnyOrder(direct, slowChange, change);
  }

  @Test
  void putsOvertakenTrainsInDifferentRoutes() {
    var trips = List.of(
      t(
        st("A", null, 1000),
        st("B", 1030, 1030),
        st("C", 1100, 1110),
        st("D", 1130, 1130),
        st("E", 1200, null)
      ),
      t(
        st("A", null, 1010),
        st("B", 1040, 1040),
        st("C", 1050, 1100),
        st("D", 1120, 1120),
        st("E", 1150, null)
      )
    );

    var result = stripTrips(planDepartAfter(trips, "A", "E", 900));

    var faster = j(List.of(
      st("A", null, 1010),
      st("B", 1040, 1040),
      st("C", 1050, 1100),
      st("D", 1120, 1120),
      st("E", 1150, null)
    ));

    assertThat(result).containsExactly(faster);
  }

  private static Transfer transfer(String from, String to, int duration) {
    return new Transfer(stop(from), stop(to), duration, 0, Integer.MAX_VALUE, null);
  }
}
