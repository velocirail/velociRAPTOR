package com.joshuaharwood.velociraptor.server.http.errorhandling;

import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceException;

/**
 * Renders a saturated {@code @Bulkhead} or a query that outran its {@code @Timeout} as an RFC 9457
 * problem, the shape every other error the API returns already uses, rather than a bespoke
 * {@code {"error": ...}} body a client would have to special-case.
 */
@ApplicationScoped
public class FaultToleranceExceptionMapper implements ExceptionMapper<FaultToleranceException> {

  /** Unchanged from the hand-built response this replaced. */
  private static final String RETRY_AFTER_SECONDS = "10";

  @Override
  public Response toResponse(FaultToleranceException exception) {
    return HttpProblem.builder()
                      .withStatus(Response.Status.SERVICE_UNAVAILABLE)
                      .withTitle("Service Unavailable")
                      .withDetail("Server at capacity, please try again later")
                      .withHeader(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                      .build()
                      .toResponse();
  }
}
