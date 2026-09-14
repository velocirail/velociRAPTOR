package com.joshuaharwood.velociraptor.loadtesting;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;

/**
 * SLA check at a fixed request rate: ramp up to {@code loadtest.rps} (default 10 req/s), hold it
 * for {@code loadtest.durationSeconds} (default 120), and assert the SLA. Use this as the
 * regression gate once {@link CapacitySimulation} has told you what rate the deployment can
 * actually sustain.
 *
 * <ul>
 *   <li>{@code loadtest.rps} - constant arrival rate to hold
 *   <li>{@code loadtest.durationSeconds} - how long to hold it
 *   <li>{@code loadtest.p95Millis} - p95 response time bound (default 1000)
 *   <li>{@code loadtest.maxFailedPercent} - failed request bound (default 1.0)
 * </ul>
 *
 * <pre>mvn -pl server-load-testing gatling:test -Dgatling.simulationClass=com.joshuaharwood.velociraptor.loadtesting.SteadyStateSimulation -Dloadtest.rps=10</pre>
 */
public class SteadyStateSimulation extends RangeQueryLoadTest {

  private static final double RPS = Double.parseDouble(System.getProperty("loadtest.rps", "10"));
  private static final int DURATION_SECONDS = Integer.getInteger("loadtest.durationSeconds", 120);
  private static final int P95_MILLIS = Integer.getInteger("loadtest.p95Millis", 1000);
  private static final double MAX_FAILED_PERCENT =
      Double.parseDouble(System.getProperty("loadtest.maxFailedPercent", "1.0"));

  {
    setUp(rangeQueryScenario.injectOpen(
            rampUsersPerSec(1).to(RPS).during(15),
            constantUsersPerSec(RPS).during(DURATION_SECONDS)))
        .protocols(httpProtocol)
        .assertions(
            global().responseTime().percentile(95.0).lt(P95_MILLIS),
            global().failedRequests().percent().lt(MAX_FAILED_PERCENT));
  }
}
