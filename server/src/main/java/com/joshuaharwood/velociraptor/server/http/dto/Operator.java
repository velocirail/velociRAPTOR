package com.joshuaharwood.velociraptor.server.http.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * The company running a train.
 *
 * @param code     the operator's ATOC code, e.g. {@code SN}, without the {@code =} gb-transit's agency ids carry; for
 *                 a TfL operator, TfL's own code
 * @param agencyId the feed's {@code agency_id} for it, which in a gb-transit feed is the National Operator Code,
 *                 {@code =SN}
 * @param name     e.g. {@code Southern}; {@code null} where the feed gives none
 * @param url      the operator's website; {@code null} where the feed gives none
 * @param phone    the operator's customer phone number; {@code null} where the feed gives none
 */
@Schema(requiredProperties = {"code", "agencyId", "name", "url", "phone"})
public record Operator(String code,
                       String agencyId,
                       @Schema(nullable = true) @Nullable String name,
                       @Schema(nullable = true) @Nullable String url,
                       @Schema(nullable = true) @Nullable String phone) {
}
