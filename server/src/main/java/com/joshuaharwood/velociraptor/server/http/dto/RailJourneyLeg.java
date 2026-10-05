package com.joshuaharwood.velociraptor.server.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.joshuaharwood.velociraptor.rail.TransitMode;
import com.joshuaharwood.velociraptor.raptor.model.PickupDropOffType;
import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.OffsetDateTime;

@Schema(oneOf = {RailJourneyLeg.RailLeg.class, RailJourneyLeg.FixedLink.class},
        discriminatorProperty = "type",
        discriminatorMapping = {
            @DiscriminatorMapping(value = "RAIL_LEG", schema = RailJourneyLeg.RailLeg.class),
            @DiscriminatorMapping(value = "FIXED_LEG", schema = RailJourneyLeg.FixedLink.class)
        })
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

    @Schema(requiredProperties = {"type", "origin", "destination", "departureTime", "arrivalTime", "originTrainUid",
        "destinationTrainUid", "trainTrip", "startIndex", "endIndex", "originPickUpType", "destinationDropOffType",
        "operator", "transitMode", "originPlatform", "destinationPlatform", "duration",
        "boardingInterchange"})
    record RailLeg(String origin,
                   String destination,
                   OffsetDateTime departureTime,
                   OffsetDateTime arrivalTime,
                   @Schema(nullable = true) @Nullable String originTrainUid,
                   @Schema(nullable = true) @Nullable String destinationTrainUid,
                   RailTrainTrip trainTrip,
                   int startIndex,
                   int endIndex,
                   PickupDropOffType originPickUpType,
                   PickupDropOffType destinationDropOffType,
                   Operator operator,
                   TransitMode transitMode,
                   @Schema(nullable = true) @Nullable String originPlatform,
                   @Schema(nullable = true) @Nullable String destinationPlatform,
                   Duration duration,
                   @Schema(nullable = true) @Nullable Duration boardingInterchange) implements RailJourneyLeg {
        // type() is not a record component, so Jackson only writes it when told to.
        @Override
        @JsonProperty("type")
        @Schema(enumeration = "RAIL_LEG")
        public String type() { return "RAIL_LEG"; }
    }

    @Schema(requiredProperties = {"type", "origin", "destination", "departureTime", "arrivalTime", "mode", "duration",
        "boardingInterchange"})
    record FixedLink(String origin,
                     String destination,
                     OffsetDateTime departureTime,
                     OffsetDateTime arrivalTime,
                     @Schema(nullable = true) @Nullable String mode,
                     Duration duration,
                     @Schema(nullable = true) @Nullable Duration boardingInterchange) implements RailJourneyLeg {
        @Override
        @JsonProperty("type")
        @Schema(enumeration = "FIXED_LEG")
        public String type() { return "FIXED_LEG"; }
    }
}
