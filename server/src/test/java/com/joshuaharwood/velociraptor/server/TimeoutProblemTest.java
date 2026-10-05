package com.joshuaharwood.velociraptor.server;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

/**
 * A search that outruns its time limit is a 503 problem saying so, through the mapper Quarkus actually registers.
 * The mapper's own unit test calls it directly, which could not show that it was never registered: without
 * {@code @Provider} the timeout fell through to the catch-all as a 500.
 */
@QuarkusTest
@TestProfile(TimeoutProblemTest.OneMillisecond.class)
class TimeoutProblemTest {

  public static class OneMillisecond implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
        "com.joshuaharwood.velociraptor.server.http.RaptorResource/Timeout/value", "1",
        "com.joshuaharwood.velociraptor.server.http.RaptorResource/Timeout/unit", "MILLIS",
        "velociraptor.raptor.servicedate.precompute", "false");
    }
  }

  @Test
  void aSearchThatOutrunsItsTimeLimitIsA503ProblemSayingSo() {
    given()
            .queryParam("orig", "BTN")
            .queryParam("dest", "MKC")
            .queryParam("startDate", "2026-06-03T08:30:00+01:00")
            .queryParam("endDate", "2026-06-03T09:30:00+01:00")
            .when().get("/detail")
            .then()
            .statusCode(503)
            .contentType(startsWith("application/problem+json"))
            .header("Retry-After", "10")
            .body("title", is("Service Unavailable"))
            .body("detail", containsString("did not finish within its time limit"))
            .body("instance", is("/detail"));
  }
}
