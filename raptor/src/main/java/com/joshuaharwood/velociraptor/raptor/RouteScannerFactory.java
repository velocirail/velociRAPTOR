package com.joshuaharwood.velociraptor.raptor;

import com.joshuaharwood.velociraptor.raptor.model.Trip;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.util.List;
import java.util.Map;

public class RouteScannerFactory {

  private final Trip[][] tripsByRoute; // [routeIdx] -> trips sorted by first departure

  public RouteScannerFactory(Map<String, List<Trip>> tripsByRoute, Object2IntMap<String> routeToIndex) {
    this.tripsByRoute = new Trip[routeToIndex.size()][];
    for (Map.Entry<String, List<Trip>> entry : tripsByRoute.entrySet()) {
      this.tripsByRoute[routeToIndex.getInt(entry.getKey())] = entry.getValue().toArray(new Trip[0]);
    }
  }

  public RouteScanner create() {
    return new RouteScanner(tripsByRoute);
  }
}
