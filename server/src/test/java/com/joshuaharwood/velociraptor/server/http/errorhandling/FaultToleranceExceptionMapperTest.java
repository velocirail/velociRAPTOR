package com.joshuaharwood.velociraptor.server.http.errorhandling;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.exceptions.BulkheadException;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.from;

/**
 * The bulkhead can only be saturated from a live server, so the mapper is exercised directly. The refusal is pinned
 * to the body the production server sends, so a release cannot change it by accident.
 */
class FaultToleranceExceptionMapperTest {

  private final FaultToleranceExceptionMapper mapper = new FaultToleranceExceptionMapper();

  @Test
  void aSaturatedBulkheadIsRefusedAsA503ThatSaysWhenToRetry() {
    assertThat(mapper.toResponse(new BulkheadException("full")))
        .returns(503, from(Response::getStatus))
        .returns(MediaType.APPLICATION_JSON_TYPE, from(Response::getMediaType))
        .returns("10", from((Response r) -> r.getHeaderString(HttpHeaders.RETRY_AFTER)))
        .returns(Map.of("error", "Server at capacity, please try again later"), from(Response::getEntity));
  }

  @Test
  void aTimedOutQueryIsRefusedTheSameWayAsAFullBulkhead() {
    Response fromBulkhead = mapper.toResponse(new BulkheadException("full"));

    assertThat(mapper.toResponse(new TimeoutException("too slow")))
        .returns(fromBulkhead.getStatus(), from(Response::getStatus))
        .returns(fromBulkhead.getMediaType(), from(Response::getMediaType))
        .returns(fromBulkhead.getEntity(), from(Response::getEntity));
  }
}
