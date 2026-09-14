package com.joshuaharwood.velociraptor.obabridge;

import com.joshuaharwood.velociraptor.gtfs.GtfsDeserialiser;
import com.joshuaharwood.velociraptor.gtfs.ExtendedGtfsRelationalDaoImpl;
import com.joshuaharwood.velociraptor.raptor.RaptorAlgorithm;
import com.joshuaharwood.velociraptor.raptor.model.Leg;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.query.RangeQuery;
import com.joshuaharwood.velociraptor.raptor.result.Journey;
import com.joshuaharwood.velociraptor.raptor.result.JourneyFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.onebusaway.gtfs.impl.calendar.CalendarServiceDataFactoryImpl;
import org.onebusaway.gtfs.model.calendar.ServiceDate;

import java.io.File;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RangeQueryIntegrationTest {

    private static final File GTFS_FILE = Paths.get("..", "fixtures", "gtfs-sample").toFile();
    private static final ServiceDate SERVICE_DATE = new ServiceDate(2026, 6, 3);
    private static final LocalDate QUERY_DATE = LocalDate.of(2026, 6, 3);

    private static final int WINDOW_START = LocalTime.of(13, 0).toSecondOfDay();
    private static final int WINDOW_END = LocalTime.of(15, 0).toSecondOfDay();

    private static RaptorAlgorithm RAPTOR;

    @BeforeAll
    static void loadGtfs() {
        ExtendedGtfsRelationalDaoImpl dao = GtfsDeserialiser.createNewDao(GTFS_FILE);
        RAPTOR = RaptorAlgorithmFactory.createFromDao(
                dao, CalendarServiceDataFactoryImpl.createService(dao), SERVICE_DATE);
    }

    static Stream<Arguments> routes() {
        return Stream.of(
                Arguments.of("Brighton -> London Victoria", "BTN", "VIC"),
                Arguments.of("London Victoria -> Brighton", "VIC", "BTN"),
                Arguments.of("London Bridge -> Brighton", "LBG", "BTN"),
                Arguments.of("Gatwick Airport -> London Bridge", "GTW", "LBG"),
                Arguments.of("London St Pancras -> Brighton", "STP", "BTN"),
                Arguments.of("Portsmouth Harbour -> Brighton", "PMH", "BTN"),
                Arguments.of("Hastings -> Brighton", "HGS", "BTN"),
                Arguments.of("Eastbourne -> Gatwick Airport", "EBN", "GTW"),
                Arguments.of("Ashford International -> Lewes", "AFK", "LWS"),
                Arguments.of("Portslade -> Farringdon", "PLD", "ZFD")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("routes")
    void rangeQueryReturnsValidJourneys(String label, String originId, String destId) {
        var query = new RangeQuery<>(RAPTOR, new JourneyFactory());
        Stop origin = new Stop(originId);
        Stop destination = new Stop(destId);

        List<Journey> journeys = query.plan(origin, destination, QUERY_DATE, WINDOW_START, WINDOW_END);

        assertThat(journeys)
                .as("%s: should find at least one journey in the 2-hour window", label)
                .isNotEmpty();

        assertThat(journeys).allSatisfy(journey -> {
            assertThat(journey.departureTime())
                    .as("departure must be at or after window start")
                    .isGreaterThanOrEqualTo(WINDOW_START);

            assertThat(journey.arrivalTime())
                    .as("arrival must be after departure")
                    .isGreaterThan(journey.departureTime());

            assertThat(journey.legs()).as("journey must have at least one leg").isNotEmpty();

            assertThat(journey.legs().getFirst().origin())
                    .as("first leg must start at origin")
                    .isEqualTo(origin);

            assertThat(journey.legs().getLast().destination())
                    .as("last leg must end at destination")
                    .isEqualTo(destination);

            assertLegsAreContiguous(journey.legs());
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("routes")
    void rangeQueryResultsAreInDepartureOrder(String label, String originId, String destId) {
        var query = new RangeQuery<>(RAPTOR, new JourneyFactory());
        List<Journey> journeys = query.plan(
                new Stop(originId), new Stop(destId), QUERY_DATE, WINDOW_START, WINDOW_END);

        if (journeys.size() < 2) return;

        for (int i = 1; i < journeys.size(); i++) {
            assertThat(journeys.get(i).departureTime())
                    .as("%s: journey %d should depart no earlier than journey %d", label, i, i - 1)
                    .isGreaterThanOrEqualTo(journeys.get(i - 1).departureTime());
        }
    }

    @Test
    void twoHourWindowProducesMultipleServicesOnBusyRoute() {
        var query = new RangeQuery<>(RAPTOR, new JourneyFactory());
        List<Journey> journeys = query.plan(
                new Stop("BTN"), new Stop("VIC"), QUERY_DATE, WINDOW_START, WINDOW_END);

        assertThat(journeys)
                .as("Brighton -> Victoria should have more than one service in a 2-hour window")
                .hasSizeGreaterThan(1);
    }

    @Test
    void noJourneysAreDuplicates() {
        var query = new RangeQuery<>(RAPTOR, new JourneyFactory());
        List<Journey> journeys = query.plan(
                new Stop("BTN"), new Stop("VIC"), QUERY_DATE, WINDOW_START, WINDOW_END);

        assertThat(journeys).doesNotHaveDuplicates();
    }

    private static void assertLegsAreContiguous(List<Leg> legs) {
        for (int i = 0; i < legs.size() - 1; i++) {
            assertThat(legs.get(i + 1).origin())
                    .as("leg %d origin must equal leg %d destination", i + 1, i)
                    .isEqualTo(legs.get(i).destination());
        }
    }
}