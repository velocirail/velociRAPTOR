package com.joshuaharwood.velociraptor.server.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the response contract in the committed OpenAPI document (which CI checks is regenerated): every field a
 * response type has is required, so a field that does not apply to a type is left out of that type rather than sent
 * as null, and a field that applies but has no value is present and null. Plain unit test - no Quarkus/S3.
 */
class OpenApiResponseSchemaTest {

  /** Relative to the server module, which is where surefire runs. */
  private static final Path DOCUMENT = Path.of("openapi", "openapi.json");

  private static JsonNode schemas() throws IOException {
    return new ObjectMapper().readTree(DOCUMENT.toFile()).path("components").path("schemas");
  }

  @ParameterizedTest
  @ValueSource(strings = {"SimpleJourney", "SimpleRailLeg", "SimpleFixedLink", "RailJourney", "RailLeg", "FixedLink",
    "RailTrainTrip", "RailStopDateTime", "Feed", "FeedSource", "FeedInfo", "FeedAttribution"})
  void everyFieldOfAResponseTypeIsRequired(String name) throws IOException {
    var schema = schemas().path(name);
    assertThat(schema.isMissingNode()).as("%s is in the document", name).isFalse();

    var properties = new TreeSet<String>();
    schema.path("properties").fieldNames().forEachRemaining(properties::add);
    var required = new TreeSet<String>();
    schema.path("required").forEach(field -> required.add(field.asText()));

    assertThat(properties).as("%s has properties", name).isNotEmpty();
    assertThat(required).as("%s: every property is required", name).isEqualTo(properties);
  }

  @Test
  void aFixedLinkHasNoTrainFields() throws IOException {
    var trainOnly = Set.of("originTrainUid", "destinationTrainUid", "operator", "originPickUpType", "destinationDropOffType");
    for (var name : Set.of("SimpleFixedLink", "FixedLink")) {
      var properties = new TreeSet<String>();
      schemas().path(name).path("properties").fieldNames().forEachRemaining(properties::add);
      assertThat(properties).as(name).doesNotContainAnyElementsOf(trainOnly);
    }
  }

  @Test
  void aLegIsOneOfARailLegAndAFixedLinkByItsType() throws IOException {
    for (var pair : new String[][]{{"SimpleLeg", "SimpleRailLeg", "SimpleFixedLink"}, {"RailJourneyLeg", "RailLeg", "FixedLink"}}) {
      var leg = schemas().path(pair[0]);
      assertThat(leg.path("discriminator").path("propertyName").asText()).as(pair[0]).isEqualTo("type");
      assertThat(leg.path("discriminator").path("mapping").path("RAIL_LEG").asText()).endsWith("/" + pair[1]);
      assertThat(leg.path("discriminator").path("mapping").path("FIXED_LEG").asText()).endsWith("/" + pair[2]);
    }
  }

  @Test
  void aTrainLegsUidIsNullableNotOptional() throws IOException {
    for (var name : Set.of("SimpleRailLeg", "RailLeg")) {
      var uid = schemas().path(name).path("properties").path("originTrainUid");
      assertThat(uid.path("type").toString()).as(name).contains("\"null\"");
    }
  }
}
