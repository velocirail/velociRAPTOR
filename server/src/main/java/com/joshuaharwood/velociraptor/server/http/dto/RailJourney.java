package com.joshuaharwood.velociraptor.server.http.dto;

import java.util.List;

public record RailJourney(String origin, String destination, List<RailJourneyLeg> legs) {
}
