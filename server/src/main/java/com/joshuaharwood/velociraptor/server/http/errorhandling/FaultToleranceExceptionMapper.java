package com.joshuaharwood.velociraptor.server.http.errorhandling;

import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.faulttolerance.exceptions.BulkheadException;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceException;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.jspecify.annotations.Nullable;

import java.net.URI;

/**
 * Renders a saturated {@code @Bulkhead} or a query that outran its {@code @Timeout} as an RFC 9457
 * problem, the shape every other error the API returns already uses, rather than a bespoke
 * {@code {"error": ...}} body a client would have to special-case.
 */
// @Provider is what registers an ExceptionMapper with Quarkus REST; without it this class is never used and a full
// bulkhead or a timeout falls through to the catch-all as a 500.
@Provider
@ApplicationScoped
public class FaultToleranceExceptionMapper implements ExceptionMapper<FaultToleranceException> {

  /** Unchanged from the hand-built response this replaced. */
  private static final String RETRY_AFTER_SECONDS = "10";

  // The request path, as the other problems carry it; absent when the mapper is called directly, as in a unit test.
  @Context
  @Nullable UriInfo uriInfo;

  @Override
  public Response toResponse(FaultToleranceException exception) {
    var problem = HttpProblem.builder()
                             .withStatus(Response.Status.SERVICE_UNAVAILABLE)
                             .withTitle("Service Unavailable")
                             .withDetail(detailFor(exception));
    if (uriInfo != null) {
      problem.withInstance(URI.create(uriInfo.getPath()));
    }
    return problem
        .withHeader(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
        .build()
        .toResponse();
  }

  /** A timed-out search is not the server being busy, and says so (BUGS.md 9.5). */
  static String detailFor(FaultToleranceException exception) {
    return switch (exception) {
      case TimeoutException _ -> "The search did not finish within its time limit, so its result was discarded. Try "
                                 + "again, or narrow the window.";
      case BulkheadException _ -> "The server is at capacity: too many searches are running at once. Try again after the "
                                  + "Retry-After interval.";
      default -> "The search could not be run: " + exception.getMessage();
    };
  }
}
