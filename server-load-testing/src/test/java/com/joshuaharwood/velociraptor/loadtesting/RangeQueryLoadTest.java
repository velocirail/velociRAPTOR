package com.joshuaharwood.velociraptor.loadtesting;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Shared plumbing for load tests against {@code GET /} (the range query endpoint): the HTTP
 * protocol, an origin/destination feeder over stations in the artificial sample feed
 * ({@code fixtures/gtfs-sample}), and a cache warm-up.
 *
 * <p>Everything is tuned with {@code -Dloadtest.*} system properties:
 *
 * <ul>
 *   <li>{@code loadtest.baseUrl} - server under test (default {@code http://localhost:8080})
 *   <li>{@code loadtest.serviceDate} - ISO date valid in the loaded GTFS (default
 *       {@code 2026-06-03}, a Wednesday in the sample feed, matching the JMH benchmarks)
 *   <li>{@code loadtest.windowMinutes} - range query window length (default 120)
 * </ul>
 *
 * <p>The first request for a service date builds and caches the whole {@code RaptorAlgorithm}
 * (tens of seconds on a full national feed), so {@link #before()} fires one blocking request to warm
 * the cache: without it every simulation would start with a burst of ~60s "responses" that are
 * really cache-build time, drowning the steady-state numbers we are after.
 */
public abstract class RangeQueryLoadTest extends Simulation {

  protected static final String BASE_URL = System.getProperty("loadtest.baseUrl", "http://localhost:8080");
  protected static final String SERVICE_DATE = System.getProperty("loadtest.serviceDate", "2026-06-03");
  private static final ZoneId LONDON = ZoneId.of("Europe/London");
  protected static final int WINDOW_MINUTES = Integer.getInteger("loadtest.windowMinutes", 120);

  /**
   * Origin/destination pairs across the sample feed: Brighton Main Line flows, journeys that change at
   * Brighton or Hastings, and Brighton to Milton Keynes, which needs a Tube leg between the London
   * termini. See fixtures/gtfs-sample/README.md.
   */
  private static final List<String[]> OD_PAIRS = List.of(
      new String[]{"BTN", "VIC"},
      new String[]{"VIC", "BTN"},
      new String[]{"STP", "BTN"},
      new String[]{"LBG", "BTN"},
      new String[]{"GTW", "VIC"},
      new String[]{"VIC", "GTW"},
      new String[]{"ECR", "HHE"},
      new String[]{"BTN", "PMH"},
      new String[]{"PMH", "BTN"},
      new String[]{"BTN", "HGS"},
      new String[]{"HGS", "AFK"},
      new String[]{"AFK", "BTN"},
      new String[]{"EBN", "GTW"},
      new String[]{"PLD", "ZFD"},
      new String[]{"BTN", "MKC"},
      new String[]{"MKC", "BTN"},
      new String[]{"HOV", "LBG"},
      new String[]{"LWS", "VIC"});

  protected final HttpProtocolBuilder httpProtocol = http
      .baseUrl(BASE_URL)
      .acceptHeader("application/json")
      // We are measuring the routing engine, not TCP connection churn: reuse connections across
      // virtual users, like a keep-alive LB in front of the service would.
      .shareConnections();

  protected final ScenarioBuilder rangeQueryScenario = scenario("Range query")
      .feed(odFeeder())
      .exec(http("GET /")
          .get("/")
          .queryParam("orig", "#{orig}")
          .queryParam("dest", "#{dest}")
          .queryParam("startDate", "#{startDate}")
          .queryParam("endDate", "#{endDate}")
          .check(status().is(200)));

  /**
   * Infinite random feeder: a random OD pair with a {@link #WINDOW_MINUTES}-long window starting
   * on a random quarter-hour between 06:00 and 20:00. Randomising the window defeats any
   * would-be response caching and spreads work across the timetable, like real traffic.
   */
  private static Iterator<Map<String, Object>> odFeeder() {
    return new Iterator<>() {
      @Override
      public boolean hasNext() {
        return true;
      }

      @Override
      public Map<String, Object> next() {
        var random = ThreadLocalRandom.current();
        String[] pair = OD_PAIRS.get(random.nextInt(OD_PAIRS.size()));
        int startMinutes = (6 * 60) + (random.nextInt((20 - 6) * 4) * 15);
        return Map.of(
            "orig", pair[0],
            "dest", pair[1],
            "startDate", atTime(startMinutes),
            "endDate", atTime(startMinutes + WINDOW_MINUTES));
      }
    };
  }

  private static String atTime(int minutesSinceMidnight) {
    // The endpoint takes instants (ISO 8601 with offset) and reads them on the Europe/London clock, so an
    // endDate past midnight still lands on the same service date server-side; here windows stay within the day.
    return LocalDate.parse(SERVICE_DATE)
                    .atStartOfDay()
                    .plusMinutes(minutesSinceMidnight)
                    .atZone(LONDON)
                    .toOffsetDateTime()
                    .toString();
  }

  /**
   * Warms the {@code RaptorAlgorithm} cache for {@link #SERVICE_DATE} with one blocking request
   * before injection starts. Fails fast if the server is not up.
   */
  @Override
  public void before() {
    // The offset's '+' must be percent-encoded by hand here; Gatling's queryParam does it for the scenario.
    var uri = URI.create("%s/?orig=BTN&dest=VIC&startDate=%s&endDate=%s".formatted(
        BASE_URL,
        URLEncoder.encode(atTime(12 * 60), StandardCharsets.UTF_8),
        URLEncoder.encode(atTime(13 * 60), StandardCharsets.UTF_8)));
    System.out.println("Warming algorithm cache for " + SERVICE_DATE + " via " + uri);
    long start = System.nanoTime();
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).version(HttpClient.Version.HTTP_1_1).build()) {
      var response = client.send(
          HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(5)).GET().build(),
          HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new IllegalStateException("Warm-up request failed with HTTP " + response.statusCode()
            + ": " + response.body());
      }
    } catch (IOException e) {
      throw new IllegalStateException("Server unreachable at " + BASE_URL
          + " - start it first (see server-load-testing/README.md)", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted during warm-up", e);
    }
    System.out.printf("Warm-up complete in %.1fs%n", (System.nanoTime() - start) / 1e9);
  }
}
