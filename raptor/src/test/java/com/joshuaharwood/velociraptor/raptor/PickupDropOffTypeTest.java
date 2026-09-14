package com.joshuaharwood.velociraptor.raptor;

import com.joshuaharwood.velociraptor.raptor.model.PickupDropOffType;
import com.joshuaharwood.velociraptor.raptor.model.Stop;
import com.joshuaharwood.velociraptor.raptor.model.StopTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PickupDropOffTypeTest {

  @Test
  void gtfsCodesMapToTypesAndOnlyNoneForbidsUse() {
    assertThat(PickupDropOffType.fromGtfs(0)).isEqualTo(PickupDropOffType.REGULAR);
    assertThat(PickupDropOffType.fromGtfs(1)).isEqualTo(PickupDropOffType.NONE);
    assertThat(PickupDropOffType.fromGtfs(2)).isEqualTo(PickupDropOffType.PHONE_AGENCY);
    assertThat(PickupDropOffType.fromGtfs(3)).isEqualTo(PickupDropOffType.COORDINATE_WITH_DRIVER);

    assertThat(PickupDropOffType.NONE.allowed()).isFalse();
    assertThat(PickupDropOffType.REGULAR.allowed()).isTrue();
    assertThat(PickupDropOffType.PHONE_AGENCY.allowed()).isTrue();
    assertThat(PickupDropOffType.COORDINATE_WITH_DRIVER.allowed()).isTrue();
  }

  @Test
  void aStopTimeDerivesBoardingFromItsTypes() {
    var requestStop = new StopTime(new Stop("R"), 100, 100, PickupDropOffType.COORDINATE_WITH_DRIVER, PickupDropOffType.NONE);
    assertThat(requestStop.canBoard()).isTrue();
    assertThat(requestStop.canAlight()).isFalse();

    // The boolean constructor keeps the older call sites meaning what they did.
    var plain = new StopTime(new Stop("A"), 100, 100, true, false);
    assertThat(plain.pickup()).isEqualTo(PickupDropOffType.REGULAR);
    assertThat(plain.dropOff()).isEqualTo(PickupDropOffType.NONE);
    assertThat(plain).isEqualTo(new StopTime(new Stop("A"), 100, 100, PickupDropOffType.REGULAR, PickupDropOffType.NONE));
  }
}
