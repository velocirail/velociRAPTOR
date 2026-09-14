package com.joshuaharwood.velociraptor.raptor.model;

import com.joshuaharwood.velociraptor.raptor.result.ConnectionIndex;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

public record ScanResultsFactory(Object2IntMap<Stop> stopToIndex, Stop[] indexToStop) {

  public ScanResults create(Map<Stop, Integer> origins) {
    final int stopCount = indexToStop.length;

    final int[] bestArrivals = new int[stopCount];
    Arrays.fill(bestArrivals, Integer.MAX_VALUE);

    final int[] round0 = new int[stopCount];
    Arrays.fill(round0, Integer.MAX_VALUE);

    for (Map.Entry<Stop, Integer> origin : origins.entrySet()) {
      final int stopIdx = stopToIndex.getInt(origin.getKey());
      // Origins not on any route are ignored, as when seeding was driven by the full stop set.
      if (stopIdx >= 0) {
        bestArrivals[stopIdx] = origin.getValue();
        round0[stopIdx] = origin.getValue();
      }
    }

    final ArrayList<int[]> kArrivals = new ArrayList<>();
    kArrivals.add(round0);

    return new ScanResults(bestArrivals, kArrivals, new ConnectionIndexImpl(stopCount), indexToStop);
  }

  private static class ConnectionIndexImpl
      extends Object2ObjectOpenHashMap<Stop, Map<Integer, ResultConnectionIndex>>
      implements ConnectionIndex {
    ConnectionIndexImpl(int expectedSize) {
      super(expectedSize);
    }
  }
}
