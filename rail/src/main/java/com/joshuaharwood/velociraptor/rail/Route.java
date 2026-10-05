package com.joshuaharwood.velociraptor.rail;

import org.jspecify.annotations.Nullable;

/**
 * The line or brand a trip runs under, as the feed's {@code routes.txt} describes it: in a gb-transit feed, an
 * operator's trains ({@code SN}), its rail replacement buses ({@code SN_RRB}) or a brand of its own.
 *
 * @param id         the GTFS route_id
 * @param shortName  e.g. {@code TfW Rail}; null where the feed gives none
 * @param longName   e.g. {@code Transport for Wales}; null where the feed gives none
 * @param colour     the line's colour as six hex digits, e.g. {@code ff0000}; null where the feed gives none
 * @param textColour the colour of text drawn on it, as six hex digits; null where the feed gives none
 * @param url        the line's web page; null where the feed gives none
 */
public record Route(String id,
                    @Nullable String shortName,
                    @Nullable String longName,
                    @Nullable String colour,
                    @Nullable String textColour,
                    @Nullable String url) {
}
