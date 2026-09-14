package com.joshuaharwood.velociraptor.server;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Starts LocalStack, uploads the GTFS fixture as {@code s3://gtfs-test/gtfs.zip} and points the server at it.
 * <p>
 * The fixture defaults to the artificial sample feed in {@code fixtures/gtfs-sample} (a directory of GTFS text
 * files, zipped on the way up). Set {@code velociraptor.test.gtfs} to another directory or an existing zip to run
 * the suite against a different feed; the assertions in {@link RaptorResourceTest} only hold for the sample.
 */
public class LocalStackS3Resource implements QuarkusTestResourceLifecycleManager {

  static final String BUCKET = "gtfs-test";
  static final String KEY = "gtfs.zip";

  /** Relative to the server module, which is where surefire runs. */
  static final Path DEFAULT_FIXTURE = Path.of("..", "fixtures", "gtfs-sample");

  private LocalStackContainer localStack;

  @Override
  public Map<String, String> start() {
    localStack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3"))
      .withServices(LocalStackContainer.Service.S3);
    localStack.start();

    var endpoint = localStack.getEndpointOverride(LocalStackContainer.Service.S3);

    try (S3Client s3 = S3Client.builder()
      .endpointOverride(endpoint)
      .credentialsProvider(StaticCredentialsProvider.create(
        AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey())))
      .region(Region.of(localStack.getRegion()))
      .build()) {

      s3.createBucket(b -> b.bucket(BUCKET));
      s3.putObject(b -> b.bucket(BUCKET).key(KEY), fixtureZip());
    }

    return Map.of(
      "velociraptor.gtfs.source.path", "s3://" + BUCKET + "/" + KEY,
      "quarkus.s3.endpoint-override", endpoint.toString(),
      "quarkus.s3.aws.credentials.type", "static",
      "quarkus.s3.aws.credentials.static-provider.access-key-id", localStack.getAccessKey(),
      "quarkus.s3.aws.credentials.static-provider.secret-access-key", localStack.getSecretKey(),
      "quarkus.s3.aws.region", localStack.getRegion()
    );
  }

  /** The fixture as a zip: a configured zip is used as it is, a directory is zipped into a temp file. */
  static Path fixtureZip() {
    Path fixture = Path.of(System.getProperty("velociraptor.test.gtfs", DEFAULT_FIXTURE.toString()));
    if (!Files.isDirectory(fixture)) {
      return fixture;
    }
    try {
      Path zip = Files.createTempFile("gtfs-sample-", ".zip");
      zip.toFile().deleteOnExit();
      try (var out = new ZipOutputStream(Files.newOutputStream(zip)); Stream<Path> files = Files.list(fixture)) {
        for (Path file : files.filter(p -> p.toString().endsWith(".txt")).sorted().toList()) {
          out.putNextEntry(new ZipEntry(file.getFileName().toString()));
          Files.copy(file, out);
          out.closeEntry();
        }
      }
      return zip;
    } catch (IOException e) {
      throw new UncheckedIOException("Could not zip GTFS fixture " + fixture, e);
    }
  }

  @Override
  public void stop() {
    if (localStack != null) localStack.stop();
  }
}
