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
    public List<SimpleJourney> rangeQuery(
            @NotBlank @QueryParam("orig") String orig,
            @NotBlank @QueryParam("dest") String dest,
            @NotNull @QueryParam("startDate") OffsetDateTime startDate,
            @NotNull @QueryParam("endDate") OffsetDateTime endDate,
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
    public List<RailJourney> detailQuery(
            @NotBlank @QueryParam("orig") String orig,
            @NotBlank @QueryParam("dest") String dest,
            @NotNull @QueryParam("startDate") OffsetDateTime startDate,
            @NotNull @QueryParam("endDate") OffsetDateTime endDate,
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
    public List<RailJourney> firstArrival(
            @NotBlank @QueryParam("orig") String orig,
            @NotBlank @QueryParam("dest") String dest,
            @NotNull @QueryParam("startDate") OffsetDateTime startDate,
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
