package com.joshuaharwood.velociraptor.server;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * Where the loaded feed came from, so an answer can be traced back to the exact bytes it was computed from.
 *
 * @param location       the configured source: an {@code s3://} URI or a local path
 * @param sha256         the SHA-256 of what was read. For a zip, the zip's own bytes, so it matches {@code sha256sum}
 *                       run on the same file. For a directory, a digest over its files in name order - each file's
 *                       name, a NUL, then its bytes - which changes whenever any file does but matches nothing outside
 *                       this server
 * @param sizeBytes      the zip's size, or the directory's files' sizes added up
 * @param s3VersionId    the S3 object version, where the bucket keeps versions; null for a path or an unversioned bucket
 * @param s3ETag         the S3 object's ETag, quotes removed; null for a path
 * @param s3LastModified when S3 says the object was last written; null for a path
 */
public record FeedSource(String location,
                         String sha256,
                         long sizeBytes,
                         @Nullable String s3VersionId,
                         @Nullable String s3ETag,
                         @Nullable Instant s3LastModified) {

  /** A short id for the feed: the first twelve hex characters of its digest, as a git commit is shortened. */
  public String id() {
    return sha256.substring(0, 12);
  }

  /** A local zip or directory. */
  static FeedSource ofPath(Path path) {
    return Files.isDirectory(path)
           ? new FeedSource(path.toString(), directoryDigest(path), directorySize(path), null, null, null)
           : new FeedSource(path.toString(), fileDigest(path), size(path), null, null, null);
  }

  static MessageDigest newDigest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Every JVM has SHA-256", e);
    }
  }

  static String hex(MessageDigest digest) {
    return HexFormat.of().formatHex(digest.digest());
  }

  private static String fileDigest(Path file) {
    var digest = newDigest();
    update(digest, file);
    return hex(digest);
  }

  private static String directoryDigest(Path directory) {
    var digest = newDigest();
    for (Path file : files(directory)) {
      digest.update(file.getFileName().toString().getBytes(StandardCharsets.UTF_8));
      digest.update((byte) 0);
      update(digest, file);
    }
    return hex(digest);
  }

  private static long directorySize(Path directory) {
    return files(directory).stream().mapToLong(FeedSource::size).sum();
  }

  /** The directory's regular files, by name. A GTFS directory is flat, so nothing below it is read. */
  private static List<Path> files(Path directory) {
    try (Stream<Path> entries = Files.list(directory)) {
      return entries.filter(Files::isRegularFile)
                    .sorted((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()))
                    .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Could not list " + directory, e);
    }
  }

  private static void update(MessageDigest digest, Path file) {
    try (InputStream in = Files.newInputStream(file)) {
      byte[] buffer = new byte[64 * 1024];
      for (int read; (read = in.read(buffer)) != -1; ) {
        digest.update(buffer, 0, read);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read " + file, e);
    }
  }

  private static long size(Path file) {
    try {
      return Files.size(file);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not size " + file, e);
    }
  }
}
