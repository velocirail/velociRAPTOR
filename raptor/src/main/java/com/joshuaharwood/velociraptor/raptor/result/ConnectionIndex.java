package com.joshuaharwood.velociraptor.raptor.result;

import java.util.Map;
import com.joshuaharwood.velociraptor.raptor.model.ResultConnectionIndex;
import com.joshuaharwood.velociraptor.raptor.model.Stop;

public interface ConnectionIndex extends Map<Stop, Map<Integer, ResultConnectionIndex>> {
}
