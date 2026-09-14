package com.joshuaharwood.velociraptor.server.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.joshuaharwood.velociraptor.raptor.model.PickupDropOffType;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.OffsetDateTime;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = RailJourneyLeg.RailLeg.class, name = "RAIL_LEG"),
    @JsonSubTypes.Type(value = RailJourneyLeg.FixedLink.class, name = "FIXED_LEG")
})
public sealed interface RailJourneyLeg permits RailJourneyLeg.RailLeg, RailJourneyLeg.FixedLink {

    String type();

    /** Arrival minus departure. */
    Duration duration();

    /**
     * The minimum interchange at this leg's origin, which had to elapse after the previous leg
     * arrived before this leg could be boarded (or, for a fixed link, started). {@code null} on the
     * first leg, which nothing precedes.
     */
    @Nullable Duration boardingInterchange();

    record RailLeg(String origin,
                   String destination,
                   OffsetDateTime departureTime,
                   OffsetDateTime arrivalTime,
                   @Nullable String originTrainUid,
                   @Nullable String destinationTrainUid,
                   RailTrainTrip trainTrip,
                   int startIndex,
                   int endIndex,
                   PickupDropOffType originPickUpType,
                   PickupDropOffType destinationDropOffType,
                   @Nullable String operator,
                   Duration duration,
                   @Nullable Duration boardingInterchange) implements RailJourneyLeg {
        // type() is not a record component, so Jackson only writes it when told to.
        @Override
        @JsonProperty("type")
        public String type() { return "RAIL_LEG"; }
    }

    record FixedLink(String origin,
                     String destination,
                     OffsetDateTime departureTime,
                     OffsetDateTime arrivalTime,
                     @Nullable String mode,
                     Duration duration,
                     @Nullable Duration boardingInterchange) implements RailJourneyLeg {
        @Override
        @JsonProperty("type")
        public String type() { return "FIXED_LEG"; }
    }
}
