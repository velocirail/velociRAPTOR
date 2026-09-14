package com.joshuaharwood.velociraptor.server;

import com.joshuaharwood.velociraptor.raptor.FixedLinkRules;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static com.joshuaharwood.velociraptor.server.VelociraptorConfig.FIXED_LINKS_FORBID_CONTIGUOUS;
import static com.joshuaharwood.velociraptor.server.VelociraptorConfig.FIXED_LINKS_FORBID_LEADING;
import static com.joshuaharwood.velociraptor.server.VelociraptorConfig.FIXED_LINKS_FORBID_TRAILING;
import static com.joshuaharwood.velociraptor.server.VelociraptorConfig.ROUTING_ALGORITHM_PRECOMPUTE;

/**
 * How the server runs the routing algorithm: whether every service date is built at startup, and
 * where a journey may use a fixed link. Assembled once from the MicroProfile Config properties in
 * {@link VelociraptorConfig} (so each is also settable as an environment variable) and logged once
 * at startup by {@link RaptorController}.
 *
 * @param precompute     build the algorithm for every service date at startup; readiness waits for it
 * @param fixedLinkRules where a journey may begin, end or chain fixed links
 */
public record RaptorAlgorithmConfig(boolean precompute, FixedLinkRules fixedLinkRules) {

  @ApplicationScoped
  static class Producer {
    // A record cannot be a normal-scoped bean (no proxy for a final class); Singleton is a
    // pseudo-scope, so this still yields one instance without one.
    @Produces
    @Singleton
    RaptorAlgorithmConfig raptorAlgorithmConfig(
        @ConfigProperty(name = ROUTING_ALGORITHM_PRECOMPUTE, defaultValue = "false") boolean precompute,
        @ConfigProperty(name = FIXED_LINKS_FORBID_LEADING, defaultValue = "true") boolean forbidLeadingFixedLink,
        @ConfigProperty(name = FIXED_LINKS_FORBID_TRAILING, defaultValue = "true") boolean forbidTrailingFixedLink,
        @ConfigProperty(name = FIXED_LINKS_FORBID_CONTIGUOUS, defaultValue = "true") boolean forbidContiguousFixedLinks) {
      return new RaptorAlgorithmConfig(precompute,
          new FixedLinkRules(forbidLeadingFixedLink, forbidTrailingFixedLink, forbidContiguousFixedLinks));
    }
  }
}
