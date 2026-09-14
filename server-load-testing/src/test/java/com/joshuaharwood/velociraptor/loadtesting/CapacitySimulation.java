package com.joshuaharwood.velociraptor.loadtesting;

import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.incrementUsersPerSec;

/**
 * Finds the load limit: an open-model stepped ramp - {@code loadtest.steps} levels of
 * {@code loadtest.stepRps} more arrivals/sec each, holding every level for
 * {@code loadtest.levelSeconds}. Because injection is open (arrival rate is forced, it does not
 * slow down when the server does), the knee is unmistakable in the report: the "responses per
 * second" chart stops tracking "requests per second", response-time percentiles turn vertical,
 * and requests start failing with timeouts.
 *
 * <p>Read the max sustainable throughput off the last level where responses/sec still equals the
 * arrival rate and p95 is acceptable; feed that into {@link SteadyStateSimulation} as the gate.
 *
 * <ul>
 *   <li>{@code loadtest.startRps} - arrival rate of the first level (default 5)
 *   <li>{@code loadtest.stepRps} - added arrivals/sec per level (default 5)
 *   <li>{@code loadtest.steps} - number of levels (default 10)
 *   <li>{@code loadtest.levelSeconds} - hold per level (default 30)
 * </ul>
 *
 * <pre>mvn -pl server-load-testing gatling:test -Dgatling.simulationClass=com.joshuaharwood.velociraptor.loadtesting.CapacitySimulation</pre>
 */
public class CapacitySimulation extends RangeQueryLoadTest {

  private static final double START_RPS = Double.parseDouble(System.getProperty("loadtest.startRps", "5"));
  private static final double STEP_RPS = Double.parseDouble(System.getProperty("loadtest.stepRps", "5"));
  private static final int STEPS = Integer.getInteger("loadtest.steps", 10);
  private static final int LEVEL_SECONDS = Integer.getInteger("loadtest.levelSeconds", 30);

  {
    setUp(rangeQueryScenario.injectOpen(
            incrementUsersPerSec(STEP_RPS)
                .times(STEPS)
                .eachLevelLasting(LEVEL_SECONDS)
                .separatedByRampsLasting(5)
                .startingFrom(START_RPS)))
        .protocols(httpProtocol)
        // Loose bound: breaching it means the ramp genuinely broke the server, which is the
        // point of the exercise - the report, not the assertion, is the deliverable here.
        .assertions(global().failedRequests().percent().lt(50.0));
  }
}
