package com.joshuaharwood.velociraptor.server.http.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * A station a journey can start, end or change at, by the code the journey endpoints take and return.
 *
 * @param code     the station's code: the CRS code for a rail station, e.g. {@code BTN}
 * @param name     e.g. {@code Brighton}
 * @param lat      latitude, WGS 84
 * @param lon      longitude, WGS 84
 * @param url      the station's page, e.g. on National Rail's site; {@code null} where the feed gives none
 * @param stepFree whether the station is step-free from street to platform: {@code true}, {@code false}, or
 *                 {@code null} where the feed does not say
 */
@Schema(requiredProperties = {"code", "name", "lat", "lon", "url", "stepFree"})
public record Station(String code,
                      String name,
                      double lat,
                      double lon,
                      @Schema(nullable = true) @Nullable String url,
                      @Schema(nullable = true) @Nullable Boolean stepFree) {
}
