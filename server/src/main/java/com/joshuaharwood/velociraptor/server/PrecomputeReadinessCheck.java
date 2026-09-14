package com.joshuaharwood.velociraptor.server;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * Custom readiness check that blocks container readiness until RAPTOR precompute completes.
 * <p>
 * Critical for Fargate deployments: ensures ALB does not route traffic to the container
 * until all service dates have been precomputed. Without this, first requests would trigger
 * cache misses and slow GTFS scans during live traffic.
 * <p>
 * When precompute is disabled, this check immediately returns UP.
 */
@Readiness
@ApplicationScoped
public class PrecomputeReadinessCheck implements HealthCheck {
  private final RaptorController controller;

  @Inject
  public PrecomputeReadinessCheck(RaptorController controller) {
    this.controller = controller;
  }

  @Override
  public HealthCheckResponse call() {
    boolean complete = controller.isPrecomputeComplete();
    return HealthCheckResponse.named("raptor-precompute")
                               .status(complete)
                               .withData("precompute_complete", complete)
                               .build();
  }
}
