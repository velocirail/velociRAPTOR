package com.joshuaharwood.velociraptor.raptor.model;

import org.jspecify.annotations.Nullable;

public record Transfer(Stop origin,
                       Stop destination,
                       int duration,
                       int startTime,
                       int endTime,
                       @Nullable String mode) {
}
