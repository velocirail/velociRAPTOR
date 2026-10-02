package com.joshuaharwood.velociraptor.server.http.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = RailJourneyLeg.RailLeg.class, name = "RAIL_LEG"),
    @JsonSubTypes.Type(value = RailJourneyLeg.FixedLink.class, name = "FIXED_LEG")
})
public sealed interface RailJourneyLeg permits RailJourneyLeg.RailLeg, RailJourneyLeg.FixedLink {

    String type();

    record RailLeg(String origin,
                   String destination,
                   OffsetDateTime departureTime,
                   OffsetDateTime arrivalTime,
                   @Nullable String originTrainUid,
                   @Nullable String destinationTrainUid,
                   RailTrainTrip trainTrip,
                   int startIndex,
                   int endIndex) implements RailJourneyLeg {
        @Override
        public String type() { return "RAIL_LEG"; }
    }

    record FixedLink(String origin,
                     String destination,
                     @Nullable OffsetDateTime departureTime,
                     @Nullable OffsetDateTime arrivalTime,
                     int durationSeconds,
                     int originInterchange,
                     int destinationInterchange) implements RailJourneyLeg {
        @Override
        public String type() { return "FIXED_LEG"; }
    }
}
