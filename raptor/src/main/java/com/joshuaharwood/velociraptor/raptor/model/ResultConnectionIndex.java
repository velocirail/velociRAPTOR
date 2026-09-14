package com.joshuaharwood.velociraptor.raptor.model;

import com.joshuaharwood.velociraptor.raptor.model.ResultConnectionIndex.ResultConnection;
import com.joshuaharwood.velociraptor.raptor.model.Leg.TransferLeg;

public sealed interface ResultConnectionIndex permits ResultConnection, TransferLeg {
  record ResultConnection(Trip trip, int startIndex, int endIndex) implements
    ResultConnectionIndex {

  }
}
