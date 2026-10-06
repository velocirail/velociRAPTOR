package com.joshuaharwood.velociraptor.server;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The trips the server plans on. */
class RailTripsTest {

  @Test
  void operatorIsTheAtocCodeWithOrWithoutTheNocPrefix() {
    assertThat(RailTrips.operatorOf("GW")).isEqualTo("GW");
    assertThat(RailTrips.operatorOf("=GW")).isEqualTo("GW");
  }
}
