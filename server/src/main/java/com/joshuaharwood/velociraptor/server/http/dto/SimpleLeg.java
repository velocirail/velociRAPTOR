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

/**
 * A leg of a range journey: a train, or a fixed link (a walk, a Tube ride, a ferry). Each carries only
 * the fields that apply to it, so a fixed link has no train UIDs, operator or pickup types at all, and
 * every field it does carry is present, {@code null} where the leg has no value for it.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = SimpleLeg.RailLeg.class, name = "RAIL_LEG"),
    @JsonSubTypes.Type(value = SimpleLeg.FixedLink.class, name = "FIXED_LEG")
})
@Schema(oneOf = {SimpleLeg.RailLeg.class, SimpleLeg.FixedLink.class},
        discriminatorProperty = "type",
        discriminatorMapping = {
            @DiscriminatorMapping(value = "RAIL_LEG", schema = SimpleLeg.RailLeg.class),
            @DiscriminatorMapping(value = "FIXED_LEG", schema = SimpleLeg.FixedLink.class)
        })
public sealed interface SimpleLeg permits SimpleLeg.RailLeg, SimpleLeg.FixedLink {

    String type();

    /** Arrival minus departure. */
    Duration duration();

    /**
     * The minimum interchange at this leg's origin, which had to elapse after the previous leg
     * arrived before this leg could be boarded (or, for a fixed link, started). {@code null} on the
     * first leg, which nothing precedes.
     */
    @Nullable Duration boardingInterchange();

    /**
     * @param originTrainUid      the train UID at the origin; {@code null} where the trip has none, as a
     *                            TfL trip does not
     * @param destinationTrainUid the train UID at the destination, which differs from the origin's where
     *                            an association changed the headcode; {@code null} as above
     * @param operator            the company running the train
     * @param route               the line or brand it runs under
     * @param transitMode         what the trip runs as; a rail replacement bus is {@code REPLACEMENT_BUS}
     * @param retailServiceId     the retail service ID ticketing knows the train by, e.g. {@code SN430003};
     *                            {@code null} where the feed gives none
     * @param headsign            where the trip is going, as a passenger reads it; {@code null} where the feed
     *                            gives none
     * @param originPlatform      the platform boarded at; {@code null} where the feed names none
     * @param destinationPlatform the platform alighted at; {@code null} where the feed names none
     */
    @Schema(name = "SimpleRailLeg", requiredProperties = {"type", "origin", "destination", "departureTime", "arrivalTime",
        "originTrainUid", "destinationTrainUid", "originPickUpType", "destinationDropOffType", "operator", "route",
        "transitMode", "retailServiceId", "headsign", "originPlatform", "destinationPlatform", "duration",
        "boardingInterchange"})
    record RailLeg(String origin,
                   String destination,
                   OffsetDateTime departureTime,
                   OffsetDateTime arrivalTime,
                   @Schema(nullable = true) @Nullable String originTrainUid,
                   @Schema(nullable = true) @Nullable String destinationTrainUid,
                   PickupDropOffType originPickUpType,
                   PickupDropOffType destinationDropOffType,
                   Operator operator,
                   Route route,
                   TransitMode transitMode,
                   @Schema(nullable = true) @Nullable String retailServiceId,
                   @Schema(nullable = true) @Nullable String headsign,
                   @Schema(nullable = true) @Nullable String originPlatform,
                   @Schema(nullable = true) @Nullable String destinationPlatform,
                   Duration duration,
                   @Schema(nullable = true) @Nullable Duration boardingInterchange) implements SimpleLeg {
        // type() is not a record component, so Jackson only writes it when told to.
        @Override
        @JsonProperty("type")
        @Schema(enumeration = "RAIL_LEG")
        public String type() { return "RAIL_LEG"; }
    }

    /** @param mode how the link is made, e.g. {@code TUBE}, {@code WALK}; {@code null} where the feed gives none */
    @Schema(name = "SimpleFixedLink", requiredProperties = {"type", "origin", "destination", "departureTime", "arrivalTime",
        "mode", "duration", "boardingInterchange"})
    record FixedLink(String origin,
                     String destination,
                     OffsetDateTime departureTime,
                     OffsetDateTime arrivalTime,
                     @Schema(nullable = true) @Nullable String mode,
                     Duration duration,
                     @Schema(nullable = true) @Nullable Duration boardingInterchange) implements SimpleLeg {
        @Override
        @JsonProperty("type")
        @Schema(enumeration = "FIXED_LEG")
        public String type() { return "FIXED_LEG"; }
    }
}
