/*
 * Copyright © 2023 Mark Raynsford <code@io7m.com> https://www.io7m.com
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR
 * IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */


package com.io7m.northpike.packaging.support;

import com.io7m.jmulticlose.core.CloseableCollection;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;
import org.apache.commons.io.file.PathUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.zip.Deflater;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

/**
 * Run {@code jpackage} to produce an app image.
 */

public final class JPAppImageMain
{
  private static final Logger LOG =
    LoggerFactory.getLogger(JPAppImageMain.class);

  private static final FileTime FILE_TIME =
    FileTime.from(
      OffsetDateTime.parse("2020-01-01T00:00:00+00:00")
        .toInstant()
    );

  private JPAppImageMain()
  {

  }

  /**
   * Run {@code jpackage}.
   *
   * @param args The command-line arguments
   *
   * @throws Exception On errors
   */

  public static void main(
    final String[] args)
    throws Exception
  {
    final var properties = new Properties();
    try (var stream = Files.newInputStream(Paths.get(args[0]))) {
      properties.load(stream);
    }

    final var osName =
      properties.getProperty("packaging.platform.os");
    final var archName =
      properties.getProperty("packaging.platform.arch");

    final var icon32 =
      Paths.get(properties.getProperty("packaging.icon32"))
        .normalize();
    final var icon64 =
      Paths.get(properties.getProperty("packaging.icon64"))
        .normalize();
    final var icon128 =
      Paths.get(properties.getProperty("packaging.icon128"))
        .normalize();

    final var appName =
      properties.getProperty("packaging.appName");
    var appVersion =
      properties.getProperty("packaging.appVersion");
    final var mainModule =
      properties.getProperty("packaging.mainModule");
    final var appType =
      properties.getProperty("packaging.appType");

    final var packageJdk =
      Paths.get(properties.getProperty("packaging.jdk"))
        .normalize();

    final var packageJre =
      Paths.get(properties.getProperty("packaging.jre"))
        .normalize();

    final var packageJars =
      Paths.get(properties.getProperty("packaging.jars"))
        .normalize();

    final var extrasDirectory =
      Paths.get(properties.getProperty("packaging.extrasDirectory"))
        .normalize();

    final var resourceDirectory =
      Paths.get(properties.getProperty("packaging.resourceDirectory"))
        .normalize();

    final var outputDirectory =
      Paths.get(properties.getProperty("packaging.outputDirectory"))
        .normalize();

    final var outputImage =
      outputDirectory.resolve(appName)
        .normalize();

    final var distribution =
      Paths.get(properties.getProperty("packaging.distribution"))
        .normalize();

    final var outputArchive =
      outputDirectory.resolve(
        String.format(
          "%s_%s_%s-%s.tgz".formatted(
            appName,
            appVersion,
            osName,
            archName
          )
        )
      );

    final var jpackagePath =
      packageJdk.resolve("bin")
        .resolve("jpackage")
        .normalize();

    Files.createDirectories(resourceDirectory);

    LOG.info("Executing jpackage...");

    /*
     * Windows evidently can't support anything other than purely numeric
     * version numbers embedded in executables.
     */

    if (SystemUtils.IS_OS_WINDOWS) {
      appVersion = appVersion.replaceAll("-SNAPSHOT", "");
    }

    final var argumentList = new ArrayList<String>();
    argumentList.add(jpackagePath.toString());
    argumentList.add("--verbose");
    argumentList.add("--type");
    argumentList.add("app-image");
    argumentList.add("--runtime-image");
    argumentList.add(packageJre.toString());
    argumentList.add("--icon");
    argumentList.add(icon64.toString());
    argumentList.add("--name");
    argumentList.add(appName);
    argumentList.add("--module");
    argumentList.add(mainModule);
    argumentList.add("--module-path");
    argumentList.add(packageJars.toString());
    argumentList.add("--app-version");
    argumentList.add(appVersion);
    argumentList.add("--resource-dir");
    argumentList.add(resourceDirectory.toString());
    argumentList.add("--dest");
    argumentList.add(outputDirectory.toString());

    switch (Objects.requireNonNull(appType, "appType")) {
      case "CommandLine" -> {
        if (SystemUtils.IS_OS_WINDOWS) {
          argumentList.add("--win-console");
        }
      }
      default -> {
        throw new IllegalArgumentException(
          "Unrecognized app type: %s".formatted(appType)
        );
      }
    }

    for (int index = 0; index < argumentList.size(); ++index) {
      LOG.info(
        "jpackage[{}]: {}",
        Integer.valueOf(index),
        argumentList.get(index)
      );
    }

    executeProgramAndLog(argumentList);
    cleanUpImage(outputDirectory, appName);
    createExtras(outputImage, distribution, properties);
    createArchive(outputArchive, outputImage);
  }

  private static void createExtras(
    final Path outputDirectory,
    final Path distribution,
    final Properties properties)
    throws IOException
  {
    final var meta =
      outputDirectory.resolve("meta");

    Files.createDirectory(meta);

    final var appName =
      properties.getProperty("packaging.appName");
    final var appVersion =
      properties.getProperty("packaging.appVersion");

    final var licenseFile =
      Paths.get(properties.getProperty("packaging.licenseFile"))
        .normalize();

    Files.copy(
      distribution.resolve(appName).resolve("bom.xml"),
      meta.resolve("bom.xml")
    );

    Files.copy(
      licenseFile,
      meta.resolve("LICENSE.txt")
    );

    final var sourceURL =
      properties.getProperty("packaging.sourceURL");
    final var scriptsURL =
      properties.getProperty("packaging.scriptsURL");

    Files.writeString(
      meta.resolve("README.txt"), """
        %s %s

        This is an application image produced using platform-specific packaging
        scripts to repackage the original platform-independent binaries.

        The original platform-independent binaries were produced from the
        sources at:

          %s
          
        Whilst the packaging scripts themselves are available at:

          %s
            """.formatted(
        appName,
        appVersion,
        sourceURL,
        scriptsURL)
    );
  }

  private static void createArchive(
    final Path outputArchive,
    final Path outputImage)
    throws Exception
  {
    final List<Path> directories;
    try (var fileStream = Files.walk(outputImage)) {
      directories = fileStream.filter(Files::isDirectory)
        .sorted()
        .toList();

    }

    final List<Path> files;
    try (var fileStream = Files.walk(outputImage)) {
      files = fileStream.filter(Files::isRegularFile)
        .sorted()
        .toList();
    }

    try (var resources = CloseableCollection.create()) {
      final var outputStream =
        resources.add(Files.newOutputStream(
          outputArchive,
          CREATE,
          TRUNCATE_EXISTING)
        );
      final var bufferedStream =
        resources.add(new BufferedOutputStream(outputStream));

      final var gzipParams = new GzipParameters();
      gzipParams.setCompressionLevel(Deflater.BEST_COMPRESSION);
      gzipParams.setOperatingSystem(255);
      gzipParams.setModificationTime(FILE_TIME.toMillis());

      final var gzipStream =
        resources.add(new GzipCompressorOutputStream(
          bufferedStream,
          gzipParams));

      final var tarOut =
        resources.add(new TarArchiveOutputStream(gzipStream, "UTF-8"));
      tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);

      for (final var directory : directories) {
        final var name =
          outputImage.getParent()
            .relativize(directory)
            .normalize();

        final var entry = new TarArchiveEntry(directory);
        entry.setName(name.toString());
        entry.setCreationTime(FILE_TIME);
        entry.setLastModifiedTime(FILE_TIME);
        entry.setLastAccessTime(FILE_TIME);
        entry.setGroupId(0L);
        entry.setUserId(0L);
        entry.setMode(0755);
        entry.setSize(0L);

        LOG.info("Tar: {}", name);
        tarOut.putArchiveEntry(entry);
        tarOut.closeArchiveEntry();
      }

      for (final var file : files) {
        final var name =
          outputImage.getParent()
            .relativize(file)
            .normalize();

        final var entry = new TarArchiveEntry(file);
        entry.setName(name.toString());
        entry.setCreationTime(FILE_TIME);
        entry.setLastModifiedTime(FILE_TIME);
        entry.setLastAccessTime(FILE_TIME);
        entry.setGroupId(0L);
        entry.setUserId(0L);
        entry.setMode(modeOfFile(file));
        entry.setSize(Files.size(file));

        LOG.info("Tar: {}", name);
        tarOut.putArchiveEntry(entry);
        Files.copy(file, tarOut);
        tarOut.closeArchiveEntry();
      }

      tarOut.flush();
      tarOut.finish();
      gzipStream.flush();
      gzipStream.finish();
      bufferedStream.flush();
      outputStream.flush();
    }
  }

  private static int modeOfFile(
    final Path file)
  {
    if (Files.isExecutable(file)) {
      return 0755;
    } else {
      return 0644;
    }
  }

  private static void executeProgramAndLog(
    final ArrayList<String> arguments)
    throws IOException, InterruptedException
  {
    final var process =
      new ProcessBuilder(arguments)
        .start();

    final var errorReader = process.errorReader();
    final var errorT =
      Thread.ofVirtual().start(() -> {
        while (true) {
          try {
            final var line = errorReader.readLine();
            if (line == null) {
              break;
            }
            LOG.error("jpackage: stderr: {}", line);
          } catch (final IOException e) {
            LOG.error("jpackage: ", e);
          }
        }
      });

    final var stdoutReader = process.inputReader();
    final var stdoutT =
      Thread.ofVirtual().start(() -> {
        while (true) {
          try {
            final var line = stdoutReader.readLine();
            if (line == null) {
              break;
            }
            LOG.error("jpackage: stdout: {}", line);
          } catch (final IOException e) {
            LOG.error("jpackage: ", e);
          }
        }
      });

    final var r = process.waitFor();
    errorT.join();
    stdoutT.join();

    if (r != 0) {
      throw new IOException("jpackage tool failed.");
    }
  }

  private static void cleanUpImage(
    final Path outputDirectory,
    final String appName)
    throws IOException
  {
    final var outputApp =
      outputDirectory.resolve(appName);

    /*
     * Don't bother trying to reduce Windows images; they are too prone to
     * failure.
     */

    final List<Path> toRemove;
    if (SystemUtils.IS_OS_WINDOWS) {
      toRemove = List.of();
    } else {
      final var runtimeDir =
        outputApp.resolve("lib")
          .resolve("runtime");

      toRemove =
        List.of(
          runtimeDir.resolve("lib")
            .resolve("server")
            .resolve("classes.jsa"),
          runtimeDir.resolve("lib")
            .resolve("server")
            .resolve("classes_nocoops.jsa"),
          runtimeDir
            .resolve("bin"),
          runtimeDir
            .resolve("legal"),
          runtimeDir
            .resolve("conf")
            .resolve("sdp"),
          outputApp.resolve("lib")
            .resolve("app")
            .resolve(".jpackage.xml")
        );
    }

    for (final var file : toRemove) {
      LOG.info("Remove {}", file);
      PathUtils.deleteDirectory(file);
    }
  }
}
