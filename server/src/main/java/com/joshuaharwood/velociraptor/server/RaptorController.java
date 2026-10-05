package com.joshuaharwood.velociraptor.server;

import com.joshuaharwood.velociraptor.gtfs.ExtendedGtfsRelationalDaoImpl;
import com.joshuaharwood.velociraptor.rail.Leg.FixedLink;
import com.joshuaharwood.velociraptor.rail.Leg.RailLeg;
import com.joshuaharwood.velociraptor.rail.RailJourneyFactory;
import com.joshuaharwood.velociraptor.rail.RailTrip;
import com.joshuaharwood.velociraptor.rail.TrainRun;
import com.joshuaharwood.velociraptor.obabridge.RaptorAlgorithmFactory;
import com.joshuaharwood.velociraptor.rail.Association;
import com.joshuaharwood.velociraptor.raptor.RaptorAlgorithm;
import com.joshuaharwood.velociraptor.raptor.model.Leg;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.query.DepartAfterQuery;
import com.joshuaharwood.velociraptor.raptor.query.RangeQuery;
import com.joshuaharwood.velociraptor.raptor.result.Journey;
import com.joshuaharwood.velociraptor.raptor.result.JourneyFactory;
import com.joshuaharwood.velociraptor.server.http.dto.*;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.services.calendar.CalendarService;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


@ApplicationScoped
public class RaptorController {
  // The railway's local zone. GTFS int times are wall-clock seconds in this zone; we resolve them
  // through it to get the correct (DST-aware) offset for the wire (see toOffset).
  private static final ZoneId LONDON = ZoneId.of("Europe/London");

  private final ExtendedGtfsRelationalDaoImpl dao;
  // Builds the RailTrips the rail result factory expects, for every service date's build.
  private final RailTrips railTrips;
  private final CalendarService calendarService;
  private final ConcurrentHashMap<ServiceDate, RaptorAlgorithm> algorithmCache = new ConcurrentHashMap<>();
  private final LongHistogram rangeQueryJourneys;
  private final LongCounter cacheMisses;
  @SuppressWarnings({"unused", "FieldCanBeLocal"})
  private final ObservableLongGauge cacheSize;
  private final RaptorAlgorithmConfig config;
  private final AtomicBoolean precomputeComplete = new AtomicBoolean(false);

  @Inject
  public RaptorController(ExtendedGtfsRelationalDaoImpl dao,
                          CalendarService calendarService,
                          @SuppressWarnings("CdiInjectionPointsInspection") OpenTelemetry openTelemetry,
                          RaptorAlgorithmConfig config) {
    this.dao = dao;
    this.railTrips = new RailTrips(dao);
    this.calendarService = calendarService;
    this.config = config;

    Meter meter = openTelemetry.getMeter(OpenTelemetryConfigNames.SCOPE_NAME);

    rangeQueryJourneys = meter.histogramBuilder(OpenTelemetryConfigNames.ROUTING_JOURNEYS_RETURNED)
                              .setDescription("Number of journeys returned per range query")
                              .ofLongs()
                              .build();

    cacheMisses = meter.counterBuilder(OpenTelemetryConfigNames.RAPTOR_CACHE_MISS)
                       .setDescription("Algorithm cache misses - each triggers a full GTFS scan for a service date")
                       .build();

    cacheSize = meter.gaugeBuilder(OpenTelemetryConfigNames.RAPTOR_CACHE_SIZE)
                     .setDescription("Number of cached RaptorAlgorithm instances (one per service date)")
                     .ofLongs()
                     .buildWithCallback(m -> m.record(algorithmCache.size()));
  }

  /**
   * @param interchange the minimum interchange at a stop, in seconds - the algorithm's per-stop table
   * Package-private so the mapping can be unit-tested without standing up the Quarkus/S3 stack.
   */
  static SimpleJourney toSimpleJourney(Journey journey, LocalDate date, ToIntFunction<Stop> interchange) {
    var simpleLegs = new ArrayList<SimpleLeg>();
    int trainLegs = 0;
    // For a leading fixed link, journey.departureTime() is the latest start that makes the first train.
    OffsetDateTime prevArrival = toOffset(date, journey.departureTime());

    var legs = journey.legs();
    for (int i = 0; i < legs.size(); i++) {
      // The interchange at this leg's origin applies between the previous leg's arrival and this
      // leg's start; the first leg has no previous leg.
      Duration boardingInterchange = i == 0 ? null : Duration.ofSeconds(interchange.applyAsInt(legs.get(i).origin()));
      switch (legs.get(i)) {
        case Leg.TimetableLeg tl -> {
          var dep = toOffset(date, tl.stopTimes().getFirst().departureTime());
          var arr = toOffset(date, tl.stopTimes().getLast().arrivalTime());
          // Every trip the server builds is a RailTrip (RailTrips), so a train leg always has an operator.
          var rt = (RailTrip) tl.trip();
          // The leg's stop times are a run of the trip's; find where it starts to read the rest by index.
          int start = rt.stopTimes().indexOf(tl.stopTimes().getFirst());
          int end = start + tl.stopTimes().size() - 1;
          // The headsign a passenger sees is the one where they board: a call's own, before a train divides, or
          // else the trip's.
          var headsign = rt.stopHeadsign(start) != null ? rt.stopHeadsign(start) : rt.headsign();
          simpleLegs.add(new SimpleLeg.RailLeg(tl.origin().id(), tl.destination().id(), dep, arr,
                                               toSmService(rt.boardingTrain(start), date),
                                               toSmService(rt.alightingTrain(end), date),
                                               tl.stopTimes().getFirst().pickup(), tl.stopTimes().getLast().dropOff(),
                                               toSmOperator(rt.operator()), toSmRoute(rt.route()), rt.mode(),
                                               headsign,
                                               rt.platform(start), rt.platform(end),
                                               toSmAssociations(rt.associations(),
                                                                k -> rt.stopTimes().get(k).stop().id(),
                                                                k -> toSmService(rt.trainAfter(k), date), start, end),
                                               Duration.between(dep, arr), boardingInterchange));
          prevArrival = arr;
          trainLegs++;
        }
        case com.joshuaharwood.velociraptor.raptor.model.Leg.TransferLeg tl -> {
          // A leading fixed link starts at the journey's departure; a later one starts once the
          // interchange at its origin has passed since the previous leg arrived.
          var dep = i == 0 ? toOffset(date, journey.departureTime())
                           : prevArrival.plusSeconds(tl.originInterchange());
          var arr = dep.plusSeconds(tl.duration());
          simpleLegs.add(new SimpleLeg.FixedLink(tl.origin().id(), tl.destination().id(), dep, arr, tl.mode(),
                                                 Duration.between(dep, arr), boardingInterchange));
          prevArrival = arr;
        }
      }
    }
    var departure = toOffset(date, journey.departureTime());
    var arrival = toOffset(date, journey.arrivalTime());
    return new SimpleJourney(departure, arrival, Duration.between(departure, arrival),
                             Math.max(0, trainLegs - 1), simpleLegs);
  }

  /**
   * The associations a leg rides through: those strictly between where it is boarded and where it is left. One at
   * either end is not stayed aboard for - the passenger boards the train that leaves, or leaves the one that came.
   */
  private static List<com.joshuaharwood.velociraptor.server.http.dto.Association> toSmAssociations(
      List<Association> associations, IntFunction<String> stopAt, IntFunction<TrainService> serviceAfter, int start,
      int end) {
    return IntStream.range(0, associations.size())
                    .filter(k -> associations.get(k).stopIndex() > start && associations.get(k).stopIndex() < end)
                    .mapToObj(k -> {
                      var a = associations.get(k);
                      return new com.joshuaharwood.velociraptor.server.http.dto.Association(
                          stopAt.apply(a.stopIndex()), a.type(), serviceAfter.apply(k), a.headsign(),
                          a.otherHeadsigns());
                    })
                    .toList();
  }

  private static List<com.joshuaharwood.velociraptor.server.http.dto.Association> toSmAssociations(
      com.joshuaharwood.velociraptor.rail.TrainTrip trip, int start, int end) {
    return toSmAssociations(trip.associations(), k -> trip.stopTimes().get(k).stop().id(),
                            k -> toSmService(trip.services().get(k + 1)), start, end);
  }

  /** The train as it runs on a day: the query's date, or the next for a train on the next day's timetable. */
  private static TrainService toSmService(TrainRun train, LocalDate date) {
    return new TrainService(train.tripId(), date.plusDays(train.dayOffset()), train.trainUid(),
                            train.retailServiceId());
  }

  private static TrainService toSmService(com.joshuaharwood.velociraptor.rail.TrainService service) {
    return new TrainService(service.tripId(), service.serviceDate(), service.trainUid(), service.retailServiceId());
  }

  /**
   * Convert seconds-since-service-date-midnight to an instant carrying the Europe/London offset,
   * rendered in local rail time (e.g. {@code 2026-06-29T07:09:00+01:00}).
   * <p>
   * GTFS int times are <em>local wall-clock</em> seconds (per the spec's "noon minus 12h" basis),
   * not elapsed real seconds, so the seconds are added on the local time-line first
   * ({@code atStartOfDay().plusSeconds}) and the offset is resolved afterwards with
   * {@code atZone(LONDON)}. On an ordinary day this is a plain {@code +01:00}/{@code Z}; on the two
   * DST-change days the zone rules apply: a time in the spring-forward gap (01:00-01:59) rolls to
   * BST, and the ambiguous fall-back hour resolves to the earlier (BST) offset. Adding elapsed
   * seconds to the midnight <em>instant</em> instead would render every post-transition time on a
   * DST-change day an hour wrong. Wall-clock {@code plusSeconds} also rolls GTFS &gt;24h
   * (after-midnight) times onto the next calendar day correctly.
   * <p>
   * Package-private so the conversion can be unit-tested without standing up the Quarkus/S3 stack.
   */
  static OffsetDateTime toOffset(LocalDate date, int seconds) {
    return date.atStartOfDay().plusSeconds(seconds).atZone(LONDON).toOffsetDateTime();
  }

  /**
   * @param interchange the minimum interchange at a stop, in seconds - the algorithm's per-stop table
   * Package-private so the mapping can be unit-tested without standing up the Quarkus/S3 stack.
   */
  static RailJourney toRailJourney(com.joshuaharwood.velociraptor.rail.RailJourney j, ToIntFunction<Stop> interchange) {
    var legs = new ArrayList<RailJourneyLeg>(j.legs().size());
    for (int i = 0; i < j.legs().size(); i++) {
      var leg = j.legs().get(i);
      Duration boardingInterchange = i == 0 ? null : Duration.ofSeconds(interchange.applyAsInt(leg.origin()));
      legs.add(toSmLeg(leg, boardingInterchange));
    }
    // Every leg has its times, a fixed link's set from the trains around it, and every journey the rail factory
    // returns has a train, so the journey runs from its first leg's departure to its last leg's arrival.
    var departure = atLondon(j.legs().getFirst().departureTime());
    var arrival = atLondon(j.legs().getLast().arrivalTime());
    var trainLegs = (int) j.legs().stream().filter(leg -> leg instanceof RailLeg).count();
    return new RailJourney(j.origin().id(), j.destination().id(), departure, arrival, Duration.between(departure, arrival),
                           Math.max(0, trainLegs - 1), List.copyOf(legs));
  }

  private static RailJourneyLeg toSmLeg(com.joshuaharwood.velociraptor.rail.Leg leg, @Nullable Duration boardingInterchange) {
    return switch (leg) {
      case RailLeg rl -> new RailJourneyLeg.RailLeg(rl.origin().id(), rl.destination().id(),
                                                    atLondon(rl.departureTime()), atLondon(rl.arrivalTime()),
                                                    toSmService(rl.originService()),
                                                    toSmService(rl.destinationService()),
                                                    toSmTrainTrip(rl.trainTrip()), rl.startIndex(), rl.endIndex(),
                                                    rl.trainTrip().stopTimes().get(rl.startIndex()).pickUpType(),
                                                    rl.trainTrip().stopTimes().get(rl.endIndex()).dropOffType(),
                                                    toSmOperator(rl.trainTrip().operator()),
                                                    toSmRoute(rl.trainTrip().route()), rl.trainTrip().mode(),
                                                    rl.trainTrip().stopTimes().get(rl.startIndex()).platform(),
                                                    rl.trainTrip().stopTimes().get(rl.endIndex()).platform(),
                                                    toSmAssociations(rl.trainTrip(), rl.startIndex(), rl.endIndex()),
                                                    rl.duration(), boardingInterchange);
      case FixedLink fl -> new RailJourneyLeg.FixedLink(fl.origin().id(), fl.destination().id(),
                                                        atLondon(fl.departureTime()), atLondon(fl.arrivalTime()),
                                                        fl.mode(), fl.duration(), boardingInterchange);
    };
  }

  /**
   * Attach the Europe/London offset to a rail-model {@link LocalDateTime} for the wire. The rail
   * module builds times as local wall-clock ({@code LocalDateTime.of(date, MIDNIGHT).plusSeconds}),
   * so {@code atZone(LONDON)} resolves the correct (DST-aware) offset - the same conversion as
   * {@link #toOffset}, applied at the /detail and /first-arrival boundary. Package-private so the conversion can be
   * unit-tested directly.
   */
  static OffsetDateTime atLondon(LocalDateTime localDateTime) {
    return localDateTime.atZone(LONDON).toOffsetDateTime();
  }

  private static RailTrainTrip toSmTrainTrip(com.joshuaharwood.velociraptor.rail.TrainTrip tt) {
    var stopTimes = tt.stopTimes().stream().map(RaptorController::toSmStopDateTime).toList();
    var services = tt.services().stream().map(RaptorController::toSmService).toList();
    return new RailTrainTrip(stopTimes, services, tt.headsign(), tt.mode());
  }

  private static Operator toSmOperator(com.joshuaharwood.velociraptor.rail.Operator operator) {
    return new Operator(operator.code(), operator.agencyId(), operator.name(), operator.url(), operator.phone());
  }

  private static Route toSmRoute(com.joshuaharwood.velociraptor.rail.Route route) {
    return new Route(route.id(), route.shortName(), route.longName(), route.colour(), route.textColour(), route.url());
  }

  private static RailStopDateTime toSmStopDateTime(com.joshuaharwood.velociraptor.rail.StopDateTime st) {
    return new RailStopDateTime(st.stop()
                                          .id(), atLondon(st.departureTime()), atLondon(st.arrivalTime()), st.isPickUp(), st.isDropOff(),
                                st.pickUpType(), st.dropOffType(), st.platform(), st.headsign());
  }

  @Startup
  public void precompute() {
    Log.infof("RAPTOR algorithm config: %s", config);
    if (!config.precompute()) {
      Log.info("RAPTOR precompute disabled.");
      precomputeComplete.set(true);
      return;
    }

    var allDates = calendarService.getServiceIds().stream()
                                  .flatMap(id -> calendarService.getServiceDatesForServiceId(id).stream())
                                  .collect(Collectors.toUnmodifiableSet());
    var total = allDates.size();
    var completed = new AtomicInteger(0);

    // Precompute if enabled
    Log.infof("Precomputing RAPTOR algorithms for %d service dates...", total);
    allDates.parallelStream().forEach(sd -> {
      getRaptorAlgorithmByDate(LocalDate.of(sd.getYear(), sd.getMonth(), sd.getDay()), true);
      Log.infof("Precompute progress: %d/%d", completed.incrementAndGet(), total);
    });
    Log.infof("Precompute complete.");
    precomputeComplete.set(true);
  }

  public boolean isPrecomputeComplete() {
    return precomputeComplete.get();
  }

  public List<SimpleJourney> rangeQuery(String origin, String destination, LocalDate date, int startTime, int endTime, List<String> notVia) {
    // The TS reference's runner, GET / : range (TimeRange) query, base journey factory rendered as simple legs.
    var raptor = getRaptorAlgorithmByDate(date, false);
    var results = new RangeQuery<>(raptor, new JourneyFactory(), config.fixedLinkRules())
        .plan(new Stop(origin), new Stop(destination), date, startTime, endTime, toStops(notVia));
    rangeQueryJourneys.record(results.size());
    return results.stream().map(j -> toSimpleJourney(j, date, raptor::interchangeTime)).toList();
  }

  public List<RailJourney> detail(String origin, String destination, LocalDate date, int startTime, int endTime, List<String> notVia) {
    // The TS reference's runner, GET /detail : range (TimeRange) query with the detailed factory.
    var raptor = getRaptorAlgorithmByDate(date, false);
    var results = new RangeQuery<>(raptor, new RailJourneyFactory(date), config.fixedLinkRules())
        .plan(new Stop(origin), new Stop(destination), date, startTime, endTime, toStops(notVia));
    return results.stream().map(j -> toRailJourney(j, raptor::interchangeTime)).toList();
  }

  public List<RailJourney> firstArrivalDetail(String origin, String destination, LocalDate date, int startTime, List<String> notVia) {
    // The TS reference's runner, GET /first-arrival : single depart-after scan with the detailed factory.
    var raptor = getRaptorAlgorithmByDate(date, false);
    var results = new DepartAfterQuery<>(raptor, new RailJourneyFactory(date), config.fixedLinkRules())
        .plan(new Stop(origin), new Stop(destination), date, startTime, toStops(notVia));
    return results.stream().map(j -> toRailJourney(j, raptor::interchangeTime)).toList();
  }

  private static Set<Stop> toStops(List<String> ids) {
    return ids.stream().map(Stop::new).collect(Collectors.toUnmodifiableSet());
  }

  private RaptorAlgorithm getRaptorAlgorithmByDate(LocalDate date, boolean precomputed) {
    var sd = new ServiceDate(date.getYear(), date.getMonthValue(), date.getDayOfMonth());

    AtomicBoolean cacheMiss = new AtomicBoolean(false);

    var algorithm = algorithmCache.computeIfAbsent(sd, d -> {
      cacheMiss.set(true);
      return RaptorAlgorithmFactory.createFromDao(dao, calendarService, d, railTrips::trip, railTrips);
    });
    if (cacheMiss.get() && !precomputed) {
      cacheMisses.add(1);
    }
    return algorithm;
  }

}
