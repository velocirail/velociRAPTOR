package com.joshuaharwood.velociraptor.raptor.result;

import java.util.List;
import com.joshuaharwood.velociraptor.raptor.model.Leg;

public record Journey(List<Leg> legs, int departureTime, int arrivalTime) {

}