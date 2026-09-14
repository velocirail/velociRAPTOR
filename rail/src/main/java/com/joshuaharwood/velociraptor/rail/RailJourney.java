package com.joshuaharwood.velociraptor.rail;

import com.joshuaharwood.velociraptor.raptor.model.Stop;

import java.util.List;

public record RailJourney(Stop origin, Stop destination, List<Leg> legs) {
}
