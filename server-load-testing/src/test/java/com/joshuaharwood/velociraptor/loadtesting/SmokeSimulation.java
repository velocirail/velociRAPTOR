package com.joshuaharwood.velociraptor.loadtesting;

import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.global;

/**
 * Sanity check, not a load test: five users at once, every request must succeed. Run this first
 * to prove the server, fixture and service date line up before burning time on a capacity run.
 *
 * <pre>mvn -pl server-load-testing gatling:test -Dgatling.simulationClass=com.joshuaharwood.velociraptor.loadtesting.SmokeSimulation</pre>
 */
public class SmokeSimulation extends RangeQueryLoadTest {

  {
    setUp(rangeQueryScenario.injectOpen(atOnceUsers(5)))
        .protocols(httpProtocol)
        .assertions(global().failedRequests().count().is(0L));
  }
}
