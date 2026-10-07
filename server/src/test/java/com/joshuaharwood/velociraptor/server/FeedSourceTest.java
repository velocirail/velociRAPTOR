package com.joshuaharwood.velociraptor.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/** Where a local feed came from: the digest that identifies it. Plain unit test - no Quarkus/S3. */
class FeedSourceTest {

  @Test
  void aZipIsDigestedAsItsOwnBytes(@TempDir Path dir) throws Exception {
    var zip = Files.write(dir.resolve("gtfs.zip"), "not really a zip".getBytes());

    var source = FeedSource.ofPath(zip);

    // What sha256sum gives for the same file.
    var expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(zip)));
    assertThat(source.sha256()).isEqualTo(expected);
    assertThat(source.id()).isEqualTo(expected.substring(0, 12));
    assertThat(source.sizeBytes()).isEqualTo(16);
    assertThat(source.location()).isEqualTo(zip.toString());
    assertThat(source.s3VersionId()).isNull();
    assertThat(source.s3ETag()).isNull();
    assertThat(source.s3LastModified()).isNull();
  }

  @Test
  void aDirectoryIsDigestedOverItsFilesWhateverOrderTheyWereWrittenIn(@TempDir Path dir) throws IOException {
    var first = Files.createDirectory(dir.resolve("first"));
    Files.writeString(first.resolve("stops.txt"), "stop_id\n");
    Files.writeString(first.resolve("agency.txt"), "agency_id\n");
    var second = Files.createDirectory(dir.resolve("second"));
    Files.writeString(second.resolve("agency.txt"), "agency_id\n");
    Files.writeString(second.resolve("stops.txt"), "stop_id\n");

    assertThat(FeedSource.ofPath(first).sha256()).isEqualTo(FeedSource.ofPath(second).sha256());
    assertThat(FeedSource.ofPath(first).sizeBytes()).isEqualTo(18);
  }

  @Test
  void aDirectorysDigestMovesWhenAnyFileDoes(@TempDir Path dir) throws IOException {
    Files.writeString(dir.resolve("agency.txt"), "agency_id\n");
    Files.writeString(dir.resolve("stops.txt"), "stop_id\n");
    var before = FeedSource.ofPath(dir).sha256();

    Files.writeString(dir.resolve("stops.txt"), "stop_id\nBTN\n");

    assertThat(FeedSource.ofPath(dir).sha256()).isNotEqualTo(before);
  }

  @Test
  void aFileMovingBetweenNamesMovesTheDigest(@TempDir Path dir) throws IOException {
    // The name is in the digest, so the same bytes under another file are another feed.
    var a = Files.createDirectory(dir.resolve("a"));
    Files.writeString(a.resolve("stops.txt"), "x\n");
    var b = Files.createDirectory(dir.resolve("b"));
    Files.writeString(b.resolve("trips.txt"), "x\n");

    assertThat(FeedSource.ofPath(a).sha256()).isNotEqualTo(FeedSource.ofPath(b).sha256());
  }
}
