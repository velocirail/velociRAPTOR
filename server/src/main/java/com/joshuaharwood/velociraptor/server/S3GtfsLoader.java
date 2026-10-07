package com.joshuaharwood.velociraptor.server;

import com.joshuaharwood.velociraptor.gtfs.ExtendedGtfsRelationalDaoImpl;
import com.joshuaharwood.velociraptor.gtfs.GtfsDeserialiser;
import com.joshuaharwood.velociraptor.gtfs.FeedFormat;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.security.DigestOutputStream;

import static com.joshuaharwood.velociraptor.server.VelociraptorConfig.GTFS_SOURCE_PATH;

/**
 * Loads GTFS data from S3 for Fargate deployments.
 * <p>
 * Expects {@code velociraptor.gtfs.source.path} to be an S3 URI in the format:
 * {@code s3://bucket-name/path/to/gtfs.zip}
 * <p>
 * Downloads the GTFS zip to a temporary file at startup, deserializes it, then deletes the temp file.
 * This loader is active when running with the {@code prod} build profile.
 * <p>
 * <b>IAM Permissions Required:</b>
 * <ul>
 *   <li>{@code s3:GetObject} on the GTFS object</li>
 *   <li>{@code s3:ListBucket} on the bucket (for error messages)</li>
 * </ul>
 * <p>
 * <b>Authentication:</b> Uses AWS SDK default credential chain:
 * <ol>
 *   <li>Fargate task IAM role (production)</li>
 *   <li>Environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)</li>
 *   <li>AWS CLI credentials (~/.aws/credentials)</li>
 * </ol>
 */
@ApplicationScoped
public class S3GtfsLoader implements GtfsLoader {
  private final String s3Uri;
  private final S3Client s3Client;

  @Inject
  public S3GtfsLoader(@ConfigProperty(name = GTFS_SOURCE_PATH) String s3Uri,
                      S3Client s3Client) {
    this.s3Uri = s3Uri;
    this.s3Client = s3Client;
  }

  @Override
  public LoadedFeed load(FeedFormat format) {
    if (!s3Uri.startsWith("s3://")) {
      throw new IllegalArgumentException(
        "Expected S3 URI in format s3://bucket/key, got: " + s3Uri);
    }

    // Parse S3 URI: s3://bucket/path/to/file.zip
    URI uri = URI.create(s3Uri);
    String bucket = uri.getHost();
    String key = uri.getPath().substring(1); // Remove leading /

    Log.infof("Downloading GTFS from S3: bucket=%s, key=%s", bucket, key);

    try {
      // Download to temp file
      File tempFile = Files.createTempFile("gtfs-", ".zip").toFile();
      tempFile.deleteOnExit();

      GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                                                           .bucket(bucket)
                                                           .key(key)
                                                           .build();

      // Digested on the way to disk, so the bytes hashed are the bytes read.
      var digest = FeedSource.newDigest();
      GetObjectResponse object;
      try (ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getObjectRequest);
           DigestOutputStream out = new DigestOutputStream(new FileOutputStream(tempFile), digest)) {
        s3Object.transferTo(out);
        object = s3Object.response();
      }

      long fileSizeBytes = tempFile.length();
      var source = new FeedSource(s3Uri, FeedSource.hex(digest), fileSizeBytes, object.versionId(),
                                  object.eTag() == null ? null : object.eTag().replace("\"", ""),
                                  object.lastModified());
      Log.infof("Downloaded GTFS to %s (%.2f MB)", tempFile.getAbsolutePath(), fileSizeBytes / 1024.0 / 1024.0);

      // Deserialize GTFS
      ExtendedGtfsRelationalDaoImpl dao = GtfsDeserialiser.createNewDao(tempFile, format);

      // Clean up temp file
      if (tempFile.delete()) {
        Log.debug("Deleted temporary GTFS file");
      } else {
        Log.warnf("Failed to delete temporary GTFS file: %s", tempFile.getAbsolutePath());
      }

      return new LoadedFeed(dao, source);

    } catch (IOException e) {
      throw new RuntimeException("Failed to download GTFS from S3: " + s3Uri, e);
    }
  }
}
