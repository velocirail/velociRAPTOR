package com.joshuaharwood.velociraptor.server.http.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * The line or brand a train runs under, as the feed describes it: in a gb-transit feed, an operator's trains
 * ({@code SN}), its rail replacement buses ({@code SN_RRB}) or a brand of its own.
 *
 * @param id         the GTFS route_id
 * @param shortName  e.g. {@code TfW Rail}; {@code null} where the feed gives none
 * @param longName   e.g. {@code Transport for Wales}; {@code null} where the feed gives none
 * @param colour     the line's colour as six hex digits without a {@code #}, e.g. {@code ff0000}; {@code null} where
 *                   the feed gives none
 * @param textColour the colour of text drawn on it, likewise; {@code null} where the feed gives none
 * @param url        the line's web page; {@code null} where the feed gives none
 */
@Schema(requiredProperties = {"id", "shortName", "longName", "colour", "textColour", "url"})
public record Route(String id,
                    @Schema(nullable = true) @Nullable String shortName,
                    @Schema(nullable = true) @Nullable String longName,
                    @Schema(nullable = true) @Nullable String colour,
                    @Schema(nullable = true) @Nullable String textColour,
                    @Schema(nullable = true) @Nullable String url) {
}
