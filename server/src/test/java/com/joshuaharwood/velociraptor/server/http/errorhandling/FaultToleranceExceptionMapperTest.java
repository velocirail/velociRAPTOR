package com.joshuaharwood.velociraptor.server.http.errorhandling;

import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.exceptions.BulkheadException;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;

/**
 * The bulkhead can only be saturated from a live server, so the mapper is exercised directly: what
 * matters is that a capacity refusal is an RFC 9457 problem like every other error, and that it still
 * carries {@code Retry-After}.
 */
class FaultToleranceExceptionMapperTest {

  private final FaultToleranceExceptionMapper mapper = new FaultToleranceExceptionMapper();

  @Test
  void aSaturatedBulkheadIsRefusedAsA503ProblemThatStillSaysWhenToRetry() {
    assertThat(mapper.toResponse(new BulkheadException("full")))
        .returns(503, from(Response::getStatus))
        .returns(HttpProblem.MEDIA_TYPE, from(Response::getMediaType))
        .returns("10", from((Response r) -> r.getHeaderString(HttpHeaders.RETRY_AFTER)));
  }

  @Test
  void theProblemCarriesTheStatusTitleAndDetail() {
    assertThat((HttpProblem) mapper.toResponse(new BulkheadException("full")).getEntity())
        .returns(503, from(HttpProblem::getStatusCode))
        .returns("Service Unavailable", from(HttpProblem::getTitle))
        .returns("Server at capacity, please try again later", from(HttpProblem::getDetail));
  }

  @Test
  void aTimedOutQueryIsRefusedTheSameWayAsAFullBulkhead() {
    Response fromBulkhead = mapper.toResponse(new BulkheadException("full"));

    assertThat(mapper.toResponse(new TimeoutException("too slow")))
        .returns(fromBulkhead.getStatus(), from(Response::getStatus))
        .returns(fromBulkhead.getMediaType(), from(Response::getMediaType))
        .returns(fromBulkhead.getHeaderString(HttpHeaders.RETRY_AFTER),
                 from((Response r) -> r.getHeaderString(HttpHeaders.RETRY_AFTER)));
  }
}
