package com.joshuaharwood.velociraptor.raptor.model;

import java.util.List;
import java.util.function.Function;

@FunctionalInterface
public interface JourneyFilter<T> extends Function<List<T>, List<T>> {
}
