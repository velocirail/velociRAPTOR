package com.joshuaharwood.velociraptor.server;

import com.joshuaharwood.velociraptor.gtfs.ExtendedGtfsRelationalDaoImpl;
import com.joshuaharwood.velociraptor.obabridge.TripLink;
import com.joshuaharwood.velociraptor.rail.AssociationType;
import com.joshuaharwood.velociraptor.rail.Operator;
import com.joshuaharwood.velociraptor.rail.RailTrip;
import com.joshuaharwood.velociraptor.rail.Route;
import com.joshuaharwood.velociraptor.rail.TrainRun;
import com.joshuaharwood.velociraptor.rail.TransitMode;
import com.joshuaharwood.velociraptor.raptor.model.Leg;
import com.joshuaharwood.velociraptor.raptor.model.Leg.TimetableLeg;
import com.joshuaharwood.velociraptor.raptor.model.PickupDropOffType;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.model.StopTime;
import com.joshuaharwood.velociraptor.raptor.result.Journey;
import com.joshuaharwood.velociraptor.server.http.dto.Association;
import com.joshuaharwood.velociraptor.server.http.dto.SimpleLeg;
import com.joshuaharwood.velociraptor.server.http.dto.TrainService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The trips the server plans on: the operator's code, and the through trip a passenger stays aboard. */
class RailTripsTest {

  private static final LocalDate DATE = LocalDate.of(2026, 10, 10);
  private static final Operator SOUTHERN =
      new Operator("SN", "=SN", "Southern", "https://www.southernrailway.com/", "0345 127 2920");
  private static final Route SOUTHERN_TRAINS = new Route("SN", null, "Southern", "8cc63e", "000000", null);
  private static final String PORTSMOUTH_TRIP = "C82504_20260523_20261212";
  private static final String BOGNOR_TRIP = "G26116_20260523_20261212";
  private static final String BOTH = "Portsmouth Harbour and Bognor Regis";

  // VIC 06:05 -> HRH 07:00 -> PMH 08:20 divides at Horsham; its portion leaves HRH 07:06 for BOG 07:52. Before the
  // divide each call shows "Portsmouth Harbour and Bognor Regis".
  private static final RailTrip PORTSMOUTH = new RailTrip(PORTSMOUTH_TRIP, List.of(
      new StopTime(new Stop("VIC"), 6 * 3600 + 300, 6 * 3600 + 300, PickupDropOffType.REGULAR, PickupDropOffType.NONE),
      new StopTime(new Stop("HRH"), 7 * 3600, 7 * 3600 + 60, PickupDropOffType.REGULAR, PickupDropOffType.REGULAR),
      new StopTime(new Stop("PMH"), 8 * 3600 + 1200, 8 * 3600 + 1200, PickupDropOffType.NONE,
                   PickupDropOffType.REGULAR)),
      List.of(new TrainRun(PORTSMOUTH_TRIP, 0, "C82504", "SN430003")), "Portsmouth Harbour", TransitMode.RAIL,
      SOUTHERN, SOUTHERN_TRAINS, List.of("18", "4", "3"), Arrays.asList(BOTH, BOTH, null), List.of(), List.of());
  private static final RailTrip BOGNOR = new RailTrip(BOGNOR_TRIP, List.of(
      new StopTime(new Stop("HRH"), 7 * 3600 + 360, 7 * 3600 + 360, PickupDropOffType.REGULAR, PickupDropOffType.NONE),
      new StopTime(new Stop("BOG"), 7 * 3600 + 3120, 7 * 3600 + 3120, PickupDropOffType.NONE,
                   PickupDropOffType.REGULAR)),
      List.of(new TrainRun(BOGNOR_TRIP, 0, "G26116", "SN430002")), "Bognor Regis", TransitMode.RAIL, SOUTHERN,
      SOUTHERN_TRAINS,
      List.of("4", "1"), List.of(), List.of(), List.of());

  @Test
  void aTrainThatDividesIsStayedAboardInThePortionForTheDestination() {
    var through = divideAtHorsham(0);

    // The through trip's own id is not a GTFS trip_id; it is made of the two trains, each with its own ids.
    assertThat(through.id()).isEqualTo(PORTSMOUTH_TRIP + "+" + BOGNOR_TRIP);
    assertThat(through.trains()).containsExactly(new TrainRun(PORTSMOUTH_TRIP, 0, "C82504", "SN430003"),
                                                 new TrainRun(BOGNOR_TRIP, 0, "G26116", "SN430002"));
    // Up to Horsham the train is the Portsmouth one; boarding there is boarding the Bognor portion.
    assertThat(through.boardingTrain(0).tripId()).isEqualTo(PORTSMOUTH_TRIP);
    assertThat(through.alightingTrain(1).tripId()).isEqualTo(PORTSMOUTH_TRIP);
    assertThat(through.boardingTrain(1).tripId()).isEqualTo(BOGNOR_TRIP);
    assertThat(through.alightingTrain(2).tripId()).isEqualTo(BOGNOR_TRIP);
    assertThat(through.platforms()).containsExactly("18", "4", "1");
    assertThat(through.headsign()).isEqualTo("Bognor Regis");
    // The timetable is kept for what a passenger may do at each call; the routed calls only board before HRH.
    assertThat(through.timetabledCall(1).canAlight()).isTrue();
    assertThat(through.stopTimes().get(1).canAlight()).isFalse();

    var simple = RaptorController.toSimpleJourney(
        new Journey(List.<Leg>of(new TimetableLeg(new Stop("VIC"), new Stop("BOG"), through.stopTimes(), through)),
                    6 * 3600 + 300, 7 * 3600 + 3120), DATE, stop -> 0);

    var portsmouth = new TrainService(PORTSMOUTH_TRIP, DATE, "C82504", "SN430003");
    var bognor = new TrainService(BOGNOR_TRIP, DATE, "G26116", "SN430002");
    assertThat(simple.changes()).isZero();
    assertThat(simple.legs()).singleElement().isInstanceOfSatisfying(SimpleLeg.RailLeg.class, rail -> {
      assertThat(rail.headsign()).isEqualTo(BOTH);
      assertThat(rail.originService()).isEqualTo(portsmouth);
      assertThat(rail.destinationService()).isEqualTo(bognor);
      assertThat(rail.associations()).containsExactly(
          new Association("HRH", AssociationType.DIVIDE, bognor, "Bognor Regis", List.of("Portsmouth Harbour")));
    });
  }

  @Test
  void aPortionOnTheFollowingServiceDayRunsOnTheNextDate() {
    // As a sleeper's portions do: the onward train runs on the next day's timetable.
    var through = divideAtHorsham(1);

    assertThat(through.trains()).extracting(TrainRun::dayOffset).containsExactly(0, 1);
    var simple = RaptorController.toSimpleJourney(
        new Journey(List.<Leg>of(new TimetableLeg(new Stop("VIC"), new Stop("BOG"), through.stopTimes(), through)),
                    6 * 3600 + 300, 7 * 3600 + 3120), DATE, stop -> 0);

    assertThat(simple.legs()).singleElement().isInstanceOfSatisfying(SimpleLeg.RailLeg.class, rail -> {
      assertThat(rail.originService().serviceDate()).isEqualTo(DATE);
      assertThat(rail.destinationService().serviceDate()).isEqualTo(DATE.plusDays(1));
      assertThat(rail.associations().getFirst().service().serviceDate()).isEqualTo(DATE.plusDays(1));
    });
  }

  @Test
  void operatorIsTheAtocCodeWithOrWithoutTheNocPrefix() {
    assertThat(RailTrips.operatorOf("GW")).isEqualTo("GW");
    assertThat(RailTrips.operatorOf("=GW")).isEqualTo("GW");
  }

  /** The Portsmouth train staying aboard into the Bognor portion, which runs {@code dayOffset} days later. */
  private static RailTrip divideAtHorsham(int dayOffset) {
    var timetable = List.of(PORTSMOUTH.stopTimes().get(0),
        new StopTime(new Stop("HRH"), 7 * 3600, 7 * 3600 + 360, PickupDropOffType.REGULAR, PickupDropOffType.REGULAR),
        BOGNOR.stopTimes().get(1));
    var routed = List.of(timetable.get(0),
        new StopTime(new Stop("HRH"), 7 * 3600, 7 * 3600 + 360, PickupDropOffType.NONE, PickupDropOffType.NONE),
        timetable.get(2));
    return (RailTrip) new RailTrips(new ExtendedGtfsRelationalDaoImpl()).link(
        new TripLink(PORTSMOUTH, BOGNOR, 1, 0, dayOffset, TripLink.Type.DIVIDE, List.of(PORTSMOUTH)), routed,
        timetable);
  }
}
