package com.joshuaharwood.velociraptor.server.http.errorhandling;

import io.quarkiverse.httpproblem.HttpProblem;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The details the library leaves empty or generic. Plain unit test - no Quarkus/S3. */
class ReadableProblemDetailsTest {

  @Test
  void anUnexpectedErrorSaysItWasLoggedRatherThanNothing() {
    var problem = HttpProblem.valueOf(Response.Status.INTERNAL_SERVER_ERROR);

    assertThat(ReadableProblemDetails.detailFor(problem, new IllegalStateException("boom"), "/detail"))
        .isEqualTo("An unexpected error stopped the request. It has been logged, with the path and the time, on the server.")
        .doesNotContain("boom");
  }

  @Test
  void aProblemThatAlreadySaysWhyIsLeftAlone() {
    var problem = HttpProblem.valueOf(Response.Status.BAD_REQUEST, "endDate=... must be after startDate=...");

    assertThat(ReadableProblemDetails.detailFor(problem, problem, "/")).isNull();
  }
}
