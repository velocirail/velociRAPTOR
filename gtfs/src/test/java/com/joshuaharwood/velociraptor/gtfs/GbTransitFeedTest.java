package com.joshuaharwood.velociraptor.gtfs;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.Trip;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reading the gb-transit sample feeds: stop keys, train UIDs, fixed links, and refusing the wrong format. */
@SuppressWarnings("removal")
class GbTransitFeedTest {

  private static final File NATIONAL_RAIL = Path.of("..", "fixtures", "gtfs-sample-gb-transit").toFile();
  private static final File RAIL_AND_TFL = Path.of("..", "fixtures", "gtfs-sample-gb-transit-rail-and-tfl").toFile();
  private static final File DTD2GTFS = Path.of("..", "fixtures", "gtfs-sample").toFile();

  private static ExtendedGtfsRelationalDaoImpl nationalRail;
  private static ExtendedGtfsRelationalDaoImpl railAndTfl;

  @BeforeAll
  static void load() {
    nationalRail = GtfsDeserialiser.createNewDao(NATIONAL_RAIL, FeedFormat.GB_TRANSIT);
    railAndTfl = GtfsDeserialiser.createNewDao(RAIL_AND_TFL, FeedFormat.GB_TRANSIT);
  }

  @Test
  void theFormatIsNamedAsItIsConfigured() {
    assertThat(FeedFormat.fromConfigValue("gb-transit")).isEqualTo(FeedFormat.GB_TRANSIT);
    assertThat(FeedFormat.fromConfigValue(" GB-Transit ")).isEqualTo(FeedFormat.GB_TRANSIT);
    assertThat(FeedFormat.fromConfigValue("dtd2gtfs")).isEqualTo(FeedFormat.DTD2GTFS);
    assertThatThrownBy(() -> FeedFormat.fromConfigValue("gtfs"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("gb-transit, dtd2gtfs");
  }

  @Test
  void aFeedReadAsTheWrongFormatIsRefused() {
    assertThatThrownBy(() -> GtfsDeserialiser.createNewDao(DTD2GTFS, FeedFormat.GB_TRANSIT))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("looks like a dtd2gtfs feed");
    assertThatThrownBy(() -> GtfsDeserialiser.createNewDao(NATIONAL_RAIL, FeedFormat.DTD2GTFS))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("has no links.txt");
  }

  @Test
  void aBoardingPointRoutesAsItsStation() {
    var profile = nationalRail.feedProfile();
    assertThat(profile.stopKey(stop(nationalRail, "910GBRGHTN"))).isEqualTo("BTN");
    assertThat(profile.stopKey(stop(nationalRail, "9100BRGHTN5"))).isEqualTo("BTN");
    // A call naming no platform is at the station's access node, which is still the station.
    assertThat(profile.stopKey(stop(nationalRail, "9100THROAKS"))).isEqualTo("TOK");
  }

  @Test
  void aTflPlatformInsideARailStationRoutesAsTheRailStation() {
    var profile = railAndTfl.feedProfile();
    assertThat(profile.stopKey(stop(railAndTfl, "9400ZZLUVIC1"))).isEqualTo("VIC");
    assertThat(profile.stopKey(stop(railAndTfl, "9400ZZLUKSX1"))).isEqualTo("STP");
    // One of its own routes under the digit-led code gb-transit gives it.
    assertThat(profile.stopKey(stop(railAndTfl, "9400ZZLUWRR1"))).isEqualTo("102");
    assertThat(profile.stopKey(stop(railAndTfl, "940GZZLUWRR"))).isEqualTo("102");
  }

  @Test
  void theTrainUidLeadsACifTripId() {
    var profile = nationalRail.feedProfile();
    assertThat(profile.trainUid(trip("C00049_20260517_20261206"))).isEqualTo("C00049");
    assertThat(profile.trainUid(trip("C00049_20260517_20261206_2"))).isEqualTo("C00049");
    assertThat(profile.trainUid(trip("tfl_VIC_N_0530"))).isNull();
    assertThat(profile.trainUid(trip("1020900"))).isNull();
  }

  @Test
  void fixedLinksAreTheRowsBetweenTwoStations() {
    // Interchange rows (a station to itself) and the split/join row (both trips set) are not links.
    assertThat(nationalRail.getAllFixedLinks())
      .allSatisfy(link -> assertThat(link.getFromStop().getId()).isNotEqualTo(link.getToStop().getId()))
      .noneMatch(link -> link.getFromStop().getId().getId().startsWith("9100"));
  }

  @Test
  void aFixedLinkIsTheEnvelopeGbTransitPublishes() {
    // The DTD's ferry is Mon-Sat 06:00-23:00 and Sun 08:00-20:00; gb-transit publishes one row covering both.
    var ferry = link(nationalRail, "910GPMTHHRB", "910GRYDPIER");
    assertThat(ferry.getMode()).isEqualTo("FERRY");
    assertThat(ferry.getDurationInSeconds()).isEqualTo(1320);
    assertThat(ferry.getStartTime()).isEqualTo(6 * 3600);
    assertThat(ferry.getEndTime()).isEqualTo(23 * 3600);
    assertThat(ferry.isSunday()).isTrue();
    assertThat(ferry.getStartDate().getAsString()).isEqualTo("20260101");
    assertThat(ferry.getEndDate().getAsString()).isEqualTo("20261231");
  }

  @Test
  void aLinkWithNoWindowIsThereAllDayEveryDay() {
    var walk = link(railAndTfl, "940GZZLUWRR", "910GEUSTON");
    assertThat(walk.getMode()).isEqualTo("WALK");
    assertThat(walk.getDurationInSeconds()).isEqualTo(300);
    assertThat(walk.getStartTime()).isEqualTo(GbTransitFixedLinks.START_OF_DAY);
    assertThat(walk.getEndTime()).isEqualTo(GbTransitFixedLinks.END_OF_DAY);
    assertThat(walk.getStartDate()).isEqualTo(GbTransitFixedLinks.EARLIEST);
    assertThat(walk.getEndDate()).isEqualTo(GbTransitFixedLinks.LATEST);
    assertThat(walk.isMonday() && walk.isTuesday() && walk.isWednesday() && walk.isThursday() && walk.isFriday()
      && walk.isSaturday() && walk.isSunday()).isTrue();
  }

  @Test
  void theRailAndTflFeedHasNoTubeLinks() {
    assertThat(railAndTfl.getAllFixedLinks()).noneMatch(link -> link.getMode().contains("TUBE"));
    assertThat(nationalRail.getAllFixedLinks()).anyMatch(link -> link.getMode().equals("TUBE"));
  }

  @Test
  void theFilesRoutingDoesNotUseAreLeftUnread() {
    assertThat(nationalRail.getAllEntitiesForType(org.onebusaway.gtfs.model.StopAreaElement.class)).isEmpty();
    assertThat(nationalRail.getAllEntitiesForType(GbTransitTransfer.class)).isEmpty();
  }

  @Test
  void theAttributionsAreReadInFileOrderWithTheirLicence() {
    var attributions = nationalRail.getAllAttributions();
    assertThat(attributions).extracting(FeedAttribution::getOrganizationName)
                            .containsExactly("Rail Delivery Group", "Department for Transport");
    var timetable = attributions.getFirst();
    assertThat(timetable.getLicence()).isEqualTo("Rail Settlement Plan data licence");
    assertThat(timetable.getUrl()).isEqualTo("https://raildata.org.uk/");
    assertThat(FeedAttribution.flag(timetable.getIsAuthority())).isTrue();
    assertThat(FeedAttribution.flag(timetable.getIsProducer())).isFalse();
  }

  @Test
  void aFeedWithNoAttributionsHasNone() {
    // The dtd2gtfs sample has no attributions.txt, and the file is optional.
    assertThat(GtfsDeserialiser.createNewDao(DTD2GTFS, FeedFormat.DTD2GTFS).getAllAttributions()).isEmpty();
  }

  @Test
  void theFeedInfoIsRead() {
    assertThat(nationalRail.getAllFeedInfos()).singleElement()
      .satisfies(info -> assertThat(info.getVersion()).isEqualTo("sample-1"));
  }

  private static Stop stop(ExtendedGtfsRelationalDaoImpl dao, String id) {
    return dao.getAllStops().stream().filter(s -> s.getId().getId().equals(id)).findFirst().orElseThrow();
  }

  private static FixedLink link(ExtendedGtfsRelationalDaoImpl dao, String from, String to) {
    return dao.getAllFixedLinks().stream()
      .filter(l -> l.getFromStop().getId().getId().equals(from) && l.getToStop().getId().getId().equals(to))
      .findFirst().orElseThrow();
  }

  private static Trip trip(String id) {
    var trip = new Trip();
    trip.setId(new AgencyAndId("NR", id));
    return trip;
  }
}
