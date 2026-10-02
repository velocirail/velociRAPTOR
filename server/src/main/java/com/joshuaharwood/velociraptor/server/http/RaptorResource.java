package com.joshuaharwood.velociraptor.server.http;

import com.joshuaharwood.velociraptor.server.RaptorController;
import com.joshuaharwood.velociraptor.server.http.dto.SimpleJourney;
import com.joshuaharwood.velociraptor.server.http.dto.RailJourney;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.quarkus.logging.Log;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.joshuaharwood.velociraptor.server.http.WindowDateTimeParamConverterProvider.LONDON;

/**
 * {@code startDate}/{@code endDate} arrive as instants (see {@link WindowDateTimeParamConverterProvider} for the
 * accepted forms) and are moved onto the Europe/London wall-clock time-line before anything else happens: that is
 * the line GTFS times live on, and it is what picks the service date.
 */
@ApplicationScoped
@Path("/")
@Tag(name = "Journey planning", description = "RAPTOR searches over the loaded GTFS feed.")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
// Sane defaults, overridable with env vars
@Bulkhead(12)
@Timeout(value = 5, unit = ChronoUnit.SECONDS)
public class RaptorResource {
    private final RaptorController raptorController;

    @Inject
    public RaptorResource(RaptorController raptorController) {
        this.raptorController = raptorController;
    }

    @GET
    @Operation(summary = "Pareto-optimal journeys in a time window",
            description = "Every journey that is not beaten on both departure and arrival, as a flat list of "
                    + "legs. The search covers a single service day.")
    @APIResponse(responseCode = "200", description = "Matching journeys, empty if none run.")
    @APIResponse(responseCode = "400",
            description = "A window parameter is not an ISO 8601 date-time, or `orig`/`dest` also appears in `notVia`.")
    @APIResponse(responseCode = "503",
            description = "Server at capacity: the bulkhead is full or the query exceeded its timeout. Retry after "
                    + "the interval in the `Retry-After` header.")
    public List<SimpleJourney> rangeQuery(
            @Parameter(description = "Origin stop id (CRS code, e.g. `BTN`). An unknown stop yields an empty list, not an error.")
            @NotBlank @QueryParam("orig") String orig,
            @Parameter(description = "Destination stop id (CRS code, e.g. `VIC`). An unknown stop yields an empty list, not an error.")
            @NotBlank @QueryParam("dest") String dest,
            @Parameter(description = "Start of the search window: ISO 8601 with a UTC offset, e.g. `2026-06-03T08:30:00+01:00` or `2026-06-03T07:30:00Z`. Read on the Europe/London clock, which is the one GTFS times run on and which picks the service date. A zone-less value (`2026-06-03T08:30:00`) is still accepted and read as London wall-clock, but is deprecated and logged at WARN.")
            @NotNull @QueryParam("startDate") OffsetDateTime startDate,
            @Parameter(description = "End of the search window, in the same form as `startDate`. May fall on the following day, which includes GTFS after-midnight (&gt; 24:00) departures rather than dropping them.")
            @NotNull @QueryParam("endDate") OffsetDateTime endDate,
            @Parameter(description = "Stop ids to avoid: journeys calling at or riding through any of them are excluded. Repeat the parameter for several stops.")
            @QueryParam("notVia") @DefaultValue("") List<String> notVia) {
        Log.debugf("GET / orig=%s dest=%s startDate=%s endDate=%s notVia=%s", orig, dest, startDate, endDate, notVia);
        validateNotVia(notVia, orig, dest);
        LocalDateTime start = railTime(startDate);
        LocalDateTime end = railTime(endDate);
        LocalDate serviceDate = start.toLocalDate();
        return raptorController.rangeQuery(orig, dest, serviceDate,
                secondsSinceServiceMidnight(serviceDate, start),
                secondsSinceServiceMidnight(serviceDate, end), notVia);
    }

    @GET
    @Path("detail")
    @Operation(summary = "As the range query, with full calling points",
            description = "The same journeys as `/`, expanded with each leg's intermediate calls, train "
                    + "identity and operator.")
    @APIResponse(responseCode = "200", description = "Matching journeys, empty if none run.")
    @APIResponse(responseCode = "400",
            description = "A window parameter is not an ISO 8601 date-time, or `orig`/`dest` also appears in `notVia`.")
    @APIResponse(responseCode = "503",
            description = "Server at capacity: the bulkhead is full or the query exceeded its timeout. Retry after "
                    + "the interval in the `Retry-After` header.")
    public List<RailJourney> detailQuery(
            @Parameter(description = "Origin stop id (CRS code, e.g. `BTN`). An unknown stop yields an empty list, not an error.")
            @NotBlank @QueryParam("orig") String orig,
            @Parameter(description = "Destination stop id (CRS code, e.g. `VIC`). An unknown stop yields an empty list, not an error.")
            @NotBlank @QueryParam("dest") String dest,
            @Parameter(description = "Start of the search window: ISO 8601 with a UTC offset, e.g. `2026-06-03T08:30:00+01:00` or `2026-06-03T07:30:00Z`. Read on the Europe/London clock, which is the one GTFS times run on and which picks the service date. A zone-less value (`2026-06-03T08:30:00`) is still accepted and read as London wall-clock, but is deprecated and logged at WARN.")
            @NotNull @QueryParam("startDate") OffsetDateTime startDate,
            @Parameter(description = "End of the search window, in the same form as `startDate`. May fall on the following day, which includes GTFS after-midnight (&gt; 24:00) departures rather than dropping them.")
            @NotNull @QueryParam("endDate") OffsetDateTime endDate,
            @Parameter(description = "Stop ids to avoid: journeys calling at or riding through any of them are excluded. Repeat the parameter for several stops.")
            @QueryParam("notVia") @DefaultValue("") List<String> notVia) {
        Log.debugf("GET /detail orig=%s dest=%s startDate=%s endDate=%s notVia=%s", orig, dest, startDate, endDate, notVia);
        validateNotVia(notVia, orig, dest);
        LocalDateTime start = railTime(startDate);
        LocalDateTime end = railTime(endDate);
        LocalDate serviceDate = start.toLocalDate();
        return raptorController.detail(orig, dest, serviceDate,
                secondsSinceServiceMidnight(serviceDate, start),
                secondsSinceServiceMidnight(serviceDate, end), notVia);
    }

    @GET
    @Path("first-arrival")
    @Operation(summary = "Earliest arrival from a single departure time",
            description = "One departure time rather than a window: the journeys reaching the destination "
                    + "soonest when leaving at or after `startDate`.")
    @APIResponse(responseCode = "200", description = "Matching journeys, empty if none run.")
    @APIResponse(responseCode = "400",
            description = "A window parameter is not an ISO 8601 date-time, or `orig`/`dest` also appears in `notVia`.")
    @APIResponse(responseCode = "503",
            description = "Server at capacity: the bulkhead is full or the query exceeded its timeout. Retry after "
                    + "the interval in the `Retry-After` header.")
    public List<RailJourney> firstArrival(
            @Parameter(description = "Origin stop id (CRS code, e.g. `BTN`). An unknown stop yields an empty list, not an error.")
            @NotBlank @QueryParam("orig") String orig,
            @Parameter(description = "Destination stop id (CRS code, e.g. `VIC`). An unknown stop yields an empty list, not an error.")
            @NotBlank @QueryParam("dest") String dest,
            @Parameter(description = "Start of the search window: ISO 8601 with a UTC offset, e.g. `2026-06-03T08:30:00+01:00` or `2026-06-03T07:30:00Z`. Read on the Europe/London clock, which is the one GTFS times run on and which picks the service date. A zone-less value (`2026-06-03T08:30:00`) is still accepted and read as London wall-clock, but is deprecated and logged at WARN.")
            @NotNull @QueryParam("startDate") OffsetDateTime startDate,
            @Parameter(description = "Stop ids to avoid: journeys calling at or riding through any of them are excluded. Repeat the parameter for several stops.")
            @QueryParam("notVia") @DefaultValue("") List<String> notVia) {
        Log.debugf("GET /first-arrival orig=%s dest=%s startDate=%s notVia=%s", orig, dest, startDate, notVia);
        validateNotVia(notVia, orig, dest);
        LocalDateTime start = railTime(startDate);
        return raptorController.firstArrivalDetail(orig, dest, start.toLocalDate(), start.toLocalTime().toSecondOfDay(), notVia);
    }

    /**
     * Seconds from the start service date's midnight to {@code instant}. Expressing the window
     * relative to the service date (rather than {@code instant.toSecondOfDay()}) lets an {@code
     * endDate} on the following day exceed 86400, so the query includes GTFS &gt;24h
     * (after-midnight) departures instead of silently dropping them.
     * <p>
     * Package-private so it can be unit-tested without standing up the Quarkus/S3 stack.
     */
    static int secondsSinceServiceMidnight(LocalDate serviceDate, LocalDateTime instant) {
        return (int) Duration.between(serviceDate.atStartOfDay(), instant).getSeconds();
    }

    /**
     * The instant as Europe/London wall-clock: the time-line GTFS times are expressed on and the one the service
     * date is read from. A {@code Z} window sent during BST therefore lands an hour later on the clock, as it should.
     * Package-private for the same reason as {@link #secondsSinceServiceMidnight}.
     */
    static LocalDateTime railTime(OffsetDateTime instant) {
        return instant.atZoneSameInstant(LONDON).toLocalDateTime();
    }

    private static void validateNotVia(List<String> notVia, String orig, String dest) {
        if (notVia.contains(orig) || notVia.contains(dest)) {
            String culprit = notVia.contains(orig) ? "Origin: " + orig : "Destination: " + dest;
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).entity(culprit + " in not via list: " + notVia).build());
        }
    }
}
