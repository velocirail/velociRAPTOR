package com.joshuaharwood.velociraptor.server;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

@QuarkusTest
@TestProfile(S3GtfsLoaderIT.Profile.class)
@WithTestResource(LocalStackS3Resource.class)
class S3GtfsLoaderIT {

  public static class Profile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      // precompute=false keeps startup fast for the smoke test
      return Map.of("velociraptor.raptor.servicedate.precompute", "false");
    }
  }

  private static final String DATE = "2026-06-03";

  @Test
  void loadsGtfsFromS3AndServesJourneys() {
    given()
      .queryParam("orig", "BTN")
      .queryParam("dest", "VIC")
      .queryParam("startDate", DATE + "T08:30:00")
      .queryParam("endDate", DATE + "T09:30:00")
      .when().get("/")
      .then()
      .statusCode(200)
      .body("size()", greaterThan(0));
  }

  @Test
  void returnsEmptyOnDateWithNoService() {
    given()
      .queryParam("orig", "BTN")
      .queryParam("dest", "VIC")
      .queryParam("startDate", "2026-05-01T08:00:00")
      .queryParam("endDate", "2026-05-01T23:59:00")
      .when().get("/")
      .then()
      .statusCode(200)
      .body("size()", is(0));
  }
}
