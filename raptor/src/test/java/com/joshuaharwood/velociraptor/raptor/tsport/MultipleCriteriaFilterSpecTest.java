package com.joshuaharwood.velociraptor.raptor.tsport;

import com.joshuaharwood.velociraptor.raptor.result.Journey;
import com.joshuaharwood.velociraptor.raptor.result.filter.MultipleCriteriaFilter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.joshuaharwood.velociraptor.raptor.TestData.j;
import static com.joshuaharwood.velociraptor.raptor.TestData.st;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1:1 port of
 * {@code ext/planarnetwork-raptor/test/unit/transfer-pattern/results/filter/MultipleCriteriaFilter.spec.ts}.
 */
class MultipleCriteriaFilterSpecTest {

  private final MultipleCriteriaFilter filter = new MultipleCriteriaFilter();

  @Test
  void removesSlowerJourneys() {
    List<Journey> journeys = List.of(
      j(List.of(st("A", null, 1000), st("B", 1030, 1035), st("C", 1100, null))),
      j(List.of(st("A", null, 900), st("B", 1030, 1035), st("C", 1100, null))),
      j(List.of(st("A", null, 1100), st("B", 1130, 1135), st("C", 1200, null))),
      j(List.of(st("A", null, 1200), st("B", 1230, 1235), st("C", 1300, null)))
    );

    assertThat(filter.apply(journeys)).containsExactly(
      j(List.of(st("A", null, 1000), st("B", 1030, 1035), st("C", 1100, null))),
      j(List.of(st("A", null, 1100), st("B", 1130, 1135), st("C", 1200, null))),
      j(List.of(st("A", null, 1200), st("B", 1230, 1235), st("C", 1300, null)))
    );
  }

  @Test
  void keepsSlowerJourneysIfTheyHaveFewerChanges() {
    List<Journey> journeys = List.of(
      j(List.of(st("A", null, 1000), st("B", 1030, 1035)), List.of(st("C", 1100, null))),
      j(List.of(st("A", null, 900), st("C", 1100, null))),
      j(List.of(st("A", null, 1100), st("B", 1130, 1135), st("C", 1200, null))),
      j(List.of(st("A", null, 1200), st("B", 1230, 1235), st("C", 1300, null)))
    );

    assertThat(filter.apply(journeys)).containsExactly(
      j(List.of(st("A", null, 900), st("C", 1100, null))),
      j(List.of(st("A", null, 1000), st("B", 1030, 1035)), List.of(st("C", 1100, null))),
      j(List.of(st("A", null, 1100), st("B", 1130, 1135), st("C", 1200, null))),
      j(List.of(st("A", null, 1200), st("B", 1230, 1235), st("C", 1300, null)))
    );
  }

  @Test
  void sortsJourneysBeforeFilteringThem() {
    List<Journey> journeys = List.of(
      j(List.of(st("A", null, 1000), st("B", 1030, 1035), st("C", 1100, null))),
      j(List.of(st("A", null, 900), st("B", 1030, 1035), st("C", 1100, null))),
      j(List.of(st("A", null, 1100), st("B", 1130, 1135), st("C", 1200, null))),
      j(List.of(st("A", null, 1200), st("B", 1230, 1235), st("C", 1300, null))),
      j(List.of(st("A", null, 1200), st("B", 1230, 1235), st("C", 1330, null)))
    );

    assertThat(filter.apply(journeys)).containsExactly(
      j(List.of(st("A", null, 1000), st("B", 1030, 1035), st("C", 1100, null))),
      j(List.of(st("A", null, 1100), st("B", 1130, 1135), st("C", 1200, null))),
      j(List.of(st("A", null, 1200), st("B", 1230, 1235), st("C", 1300, null)))
    );
  }
}
