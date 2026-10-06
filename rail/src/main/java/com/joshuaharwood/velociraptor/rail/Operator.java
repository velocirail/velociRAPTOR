package com.joshuaharwood.velociraptor.rail;

import org.jspecify.annotations.Nullable;

/**
 * The company running a trip, as the feed's {@code agency.txt} describes it.
 *
 * @param code     the operator's ATOC code, e.g. {@code SN}; for a TfL operator, TfL's own code
 * @param agencyId the feed's {@code agency_id} for it, which in a gb-transit feed is the National Operator Code,
 *                 {@code =SN}
 * @param name     e.g. {@code Southern}; null where the feed gives none
 * @param url      the operator's website; null where the feed gives none
 * @param phone    the operator's customer phone number; null where the feed gives none
 */
public record Operator(String code,
                       String agencyId,
                       @Nullable String name,
                       @Nullable String url,
                       @Nullable String phone) {
}
