package com.joshuaharwood.velociraptor.server;

import com.joshuaharwood.velociraptor.gtfs.ExtendedGtfsRelationalDaoImpl;
import com.joshuaharwood.velociraptor.rail.Leg.FixedLink;
import com.joshuaharwood.velociraptor.rail.Leg.RailLeg;
import com.joshuaharwood.velociraptor.rail.RailJourneyFactory;
import com.joshuaharwood.velociraptor.rail.RailTrip;
import com.joshuaharwood.velociraptor.obabridge.RaptorAlgorithmFactory;
import com.joshuaharwood.velociraptor.raptor.RaptorAlgorithm;
import com.joshuaharwood.velociraptor.raptor.model.Leg;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.model.StopTime;
import com.joshuaharwood.velociraptor.raptor.model.Trip;
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
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.services.calendar.CalendarService;

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
import java.util.function.BiFunction;
import java.util.stream.Collectors;


@ApplicationScoped
public class RaptorController {
  // The railway's local zone. GTFS int times are wall-clock seconds in this zone; we resolve them
  // through it to get the correct (DST-aware) offset for the wire (see toOffset).
  private static final ZoneId LONDON = ZoneId.of("Europe/London");

  // Builds the RailTrip the rail result factory expects, carrying the GTFS metadata
  // (serviceId/agencyId/trainUid) the raptor core treats as opaque. Shared by the single-day and
  // multi-day-overlay builds so both produce the same trip type.
  private static final BiFunction<org.onebusaway.gtfs.model.Trip, List<StopTime>, Trip> RAIL_TRIP_FACTORY =
      (obaTrip, stopTimes) -> new RailTrip(
          obaTrip.getId().getId(),
          stopTimes,
          obaTrip.getServiceId().getId(),
          obaTrip.getRoute().getAgency().getId(),
          // The ATOC/CIF train UID (e.g. W45490) lives in GTFS trip_headsign, matching the TS reference's
          // loader (trainUid = row.trip_headsign). The numeric trip_id is kept as id().
          obaTrip.getTripHeadsign());

  private final ExtendedGtfsRelationalDaoImpl dao;
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
   * The range endpoint's response, in the shape the production server has always sent: legs only, with the six
   * fields its consumer reads. Package-private so the mapping can be unit-tested without the Quarkus/S3 stack.
   */
  static SimpleJourney toSimpleJourney(Journey journey, LocalDate date) {
    var simpleLegs = new ArrayList<SimpleLeg>();
    // For a leading fixed link, journey.departureTime() is the latest start that makes the first train.
    OffsetDateTime prevArrival = toOffset(date, journey.departureTime());

    var legs = journey.legs();
    for (int i = 0; i < legs.size(); i++) {
      switch (legs.get(i)) {
        case Leg.TimetableLeg tl -> {
          var dep = toOffset(date, tl.stopTimes().getFirst().departureTime());
          var arr = toOffset(date, tl.stopTimes().getLast().arrivalTime());
          String originUid = null, destUid = null;
          if (tl.trip() instanceof RailTrip rt && rt.trainUid() != null) {
            var uids = rt.trainUid().split("_");
            originUid = uids[0];
            destUid = uids[uids.length - 1];
          }
          simpleLegs.add(new SimpleLeg(tl.origin().id(), tl.destination().id(), dep, arr, originUid, destUid));
          prevArrival = arr;
        }
        case com.joshuaharwood.velociraptor.raptor.model.Leg.TransferLeg tl -> {
          // A leading fixed link starts at the journey's departure; a later one starts once the
          // interchange at its origin has passed since the previous leg arrived.
          var dep = i == 0 ? toOffset(date, journey.departureTime())
                           : prevArrival.plusSeconds(tl.originInterchange());
          var arr = dep.plusSeconds(tl.duration());
          // A fixed link has no train. Its UIDs are empty strings, not null: the consumer of this endpoint calls
          // equals("") on both, so null would break it. Keep this until consumers read null as absent.
          simpleLegs.add(new SimpleLeg(tl.origin().id(), tl.destination().id(), dep, arr, "", ""));
          prevArrival = arr;
        }
      }
    }
    return new SimpleJourney(simpleLegs);
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
   * The detail endpoints' response, in the shape the production server has always sent. Package-private so the
   * mapping can be unit-tested without the Quarkus/S3 stack.
   */
  static RailJourney toRailJourney(com.joshuaharwood.velociraptor.rail.RailJourney j) {
    var legs = j.legs().stream().map(RaptorController::toSmLeg).toList();
    return new RailJourney(j.origin().id(), j.destination().id(), legs);
  }

  private static RailJourneyLeg toSmLeg(com.joshuaharwood.velociraptor.rail.Leg leg) {
    return switch (leg) {
      case RailLeg rl -> new RailJourneyLeg.RailLeg(rl.origin().id(), rl.destination().id(),
                                                    atLondon(rl.departureTime()), atLondon(rl.arrivalTime()),
                                                    rl.originTrainUid(), rl.destinationTrainUid(),
                                                    toSmTrainTrip(rl.trainTrip()), rl.startIndex(), rl.endIndex());
      // The rail model now knows a fixed link's times, but the production response has always carried them as
      // null on this endpoint. They stay null until the response-shape change ships on its own.
      case FixedLink fl -> new RailJourneyLeg.FixedLink(fl.origin().id(), fl.destination().id(), null, null,
                                                        fl.durationSeconds(), fl.originInterchange(),
                                                        fl.destinationInterchange());
    };
  }

  /**
   * Attach the Europe/London offset to a rail-model {@link LocalDateTime} for the wire. The rail
   * module builds times as local wall-clock ({@code LocalDateTime.of(date, MIDNIGHT).plusSeconds}),
   * so {@code atZone(LONDON)} resolves the correct (DST-aware) offset - the same conversion as
   * {@link #toOffset}, applied at the /detail and /first-arrival boundary. Null-safe: fixed-link legs
   * carry no scheduled times. Package-private so the conversion can be unit-tested directly.
   */
  static @org.jspecify.annotations.Nullable OffsetDateTime atLondon(@org.jspecify.annotations.Nullable LocalDateTime localDateTime) {
    return localDateTime == null ? null : localDateTime.atZone(LONDON).toOffsetDateTime();
  }

  private static RailTrainTrip toSmTrainTrip(com.joshuaharwood.velociraptor.rail.TrainTrip tt) {
    var stopTimes = tt.stopTimes().stream().map(RaptorController::toSmStopDateTime).toList();
    return new RailTrainTrip(tt.tripId(), stopTimes, tt.serviceId(), tt.agencyId(), tt.trainUid());
  }

  private static RailStopDateTime toSmStopDateTime(com.joshuaharwood.velociraptor.rail.StopDateTime st) {
    return new RailStopDateTime(st.stop()
                                          .id(), atLondon(st.departureTime()), atLondon(st.arrivalTime()), st.isPickUp(), st.isDropOff());
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
    return results.stream().map(j -> toSimpleJourney(j, date)).toList();
  }

  public List<RailJourney> detail(String origin, String destination, LocalDate date, int startTime, int endTime, List<String> notVia) {
    // The TS reference's runner, GET /detail : range (TimeRange) query with the detailed factory.
    var raptor = getRaptorAlgorithmByDate(date, false);
    var results = new RangeQuery<>(raptor, new RailJourneyFactory(date), config.fixedLinkRules())
        .plan(new Stop(origin), new Stop(destination), date, startTime, endTime, toStops(notVia));
    return results.stream().map(RaptorController::toRailJourney).toList();
  }

  public List<RailJourney> firstArrivalDetail(String origin, String destination, LocalDate date, int startTime, List<String> notVia) {
    // The TS reference's runner, GET /first-arrival : single depart-after scan with the detailed factory.
    var raptor = getRaptorAlgorithmByDate(date, false);
    var results = new DepartAfterQuery<>(raptor, new RailJourneyFactory(date), config.fixedLinkRules())
        .plan(new Stop(origin), new Stop(destination), date, startTime, toStops(notVia));
    return results.stream().map(RaptorController::toRailJourney).toList();
  }

  private static Set<Stop> toStops(List<String> ids) {
    return ids.stream().map(Stop::new).collect(Collectors.toUnmodifiableSet());
  }

  private RaptorAlgorithm getRaptorAlgorithmByDate(LocalDate date, boolean precomputed) {
    var sd = new ServiceDate(date.getYear(), date.getMonthValue(), date.getDayOfMonth());

    AtomicBoolean cacheMiss = new AtomicBoolean(false);

    var algorithm = algorithmCache.computeIfAbsent(sd, d -> {
      cacheMiss.set(true);
      return RaptorAlgorithmFactory.createFromDao(dao, calendarService, d, RAIL_TRIP_FACTORY);
    });
    if (cacheMiss.get() && !precomputed) {
      cacheMisses.add(1);
    }
    return algorithm;
  }

}
