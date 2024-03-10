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
import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;
import org.apache.commons.io.file.PathUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.zip.Deflater;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

/**
 * Run {@code jpackage} to produce a deb package.
 */

public final class JPDebMain
{
  private static final Logger LOG =
    LoggerFactory.getLogger(JPDebMain.class);

  private static final FileTime FILE_TIME =
    FileTime.from(
      OffsetDateTime.parse("2020-01-01T00:00:00+00:00")
        .toInstant()
    );

  private JPDebMain()
  {

  }

  private enum ArchiveType
  {
    TGZ,
    ZIP
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
    final var appVersion =
      properties.getProperty("packaging.appVersion");
    final var mainModule =
      properties.getProperty("packaging.mainModule");
    final var appType =
      properties.getProperty("packaging.appType");
    final var archiveType =
      ArchiveType.valueOf(properties.getProperty("packaging.archiveType"));

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

    final var distribution =
      Paths.get(properties.getProperty("packaging.distribution"))
        .normalize();

    final var jpackagePath =
      packageJdk.resolve("bin")
        .resolve("jpackage")
        .normalize();

    Files.createDirectories(resourceDirectory);

    LOG.info("Executing jpackage...");

    final var argumentList = new ArrayList<String>();
    argumentList.add(jpackagePath.toString());
    argumentList.add("--verbose");
    argumentList.add("--type");
    argumentList.add("deb");
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
    argumentList.add("--linux-deb-maintainer");
    argumentList.add("code@io7m.com");
    argumentList.add("--linux-package-name");
    argumentList.add(appName);
    argumentList.add("--input");
    argumentList.add(extrasDirectory.toString());

    for (int index = 0; index < argumentList.size(); ++index) {
      LOG.info(
        "jpackage[{}]: {}",
        Integer.valueOf(index),
        argumentList.get(index)
      );
    }

    createExtras(extrasDirectory, distribution, properties);
    executeProgramAndLog(argumentList);
  }

  private static void createExtras(
    final Path outputDirectory,
    final Path distribution,
    final Properties properties)
    throws IOException
  {
    Files.createDirectories(outputDirectory);

    final var appName =
      properties.getProperty("packaging.appName");
    final var appVersion =
      properties.getProperty("packaging.appVersion");

    final var licenseFile =
      Paths.get(properties.getProperty("packaging.licenseFile"))
        .normalize();

    Files.copy(
      distribution.resolve(appName).resolve("bom.xml"),
      outputDirectory.resolve("bom.xml")
    );

    Files.copy(
      licenseFile,
      outputDirectory.resolve("LICENSE.txt")
    );

    final var sourceURL =
      properties.getProperty("packaging.sourceURL");
    final var scriptsURL =
      properties.getProperty("packaging.scriptsURL");

    Files.writeString(
      outputDirectory.resolve("README.txt"), """
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
}
