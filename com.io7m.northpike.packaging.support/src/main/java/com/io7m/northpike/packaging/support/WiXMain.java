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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.Callable;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.util.Objects.requireNonNull;

/**
 * Run {@code WiX} to produce an app installer.
 */

public final class WiXMain implements Callable<Void>
{
  private static final Logger LOG =
    LoggerFactory.getLogger(WiXMain.class);

  private static final String WIX_NAMESPACE =
    "http://wixtoolset.org/schemas/v4/wxs";

  private final Path outputWixFile;
  private final String appName;
  private final Path inputDistribution;
  private final String appLongName;
  private final String appVersion;
  private final String appUpgradeCode;
  private final Path icon;

  private WiXMain(
    final Properties properties)
  {
    this.outputWixFile =
      Paths.get(properties.getProperty("packaging.outputWixFile"))
        .normalize();

    this.appName = properties.getProperty("packaging.appName");
    requireNonNull(this.appName, "appName");

    this.inputDistribution =
      Paths.get(properties.getProperty("packaging.distribution"))
        .resolve(this.appName)
        .normalize();

    this.appLongName = properties.getProperty("packaging.appLongName");
    requireNonNull(this.appLongName, "appLongName");

    this.appVersion = properties.getProperty("packaging.appVersion").replace(
      "-SNAPSHOT",
      "");
    requireNonNull(this.appVersion, "appVersion");

    this.appUpgradeCode = properties.getProperty("packaging.upgradeCode");
    requireNonNull(this.appUpgradeCode, "appUpgradeCode");

    this.icon = Paths.get(properties.getProperty("packaging.icon64")).toAbsolutePath().normalize();
    requireNonNull(this.icon, "icon");
  }

  /**
   * Run {@code WiX}.
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

    new WiXMain(properties)
      .call();
  }

  private void writePackage(final XMLStreamWriter xmlOutput)
    throws Exception
  {
    xmlOutput.writeStartElement(WIX_NAMESPACE, "Package");
    xmlOutput.writeAttribute("Language", "1033");
    xmlOutput.writeAttribute("Manufacturer", "io7m");
    xmlOutput.writeAttribute("Name", this.appLongName);
    xmlOutput.writeAttribute("Version", this.appVersion);
    xmlOutput.writeAttribute("UpgradeCode", this.appUpgradeCode);

    /*
     * Disallow downgrades.
     */

    {
      xmlOutput.writeStartElement(WIX_NAMESPACE, "MajorUpgrade");
      xmlOutput.writeAttribute(
        "DowngradeErrorMessage",
        "A newer version of [ProductName] is already installed.");
      xmlOutput.writeEndElement();
    }

    {
      xmlOutput.writeStartElement(WIX_NAMESPACE, "Icon");
      xmlOutput.writeAttribute("Id", "Icon.ico");
      xmlOutput.writeAttribute("SourceFile", this.icon.toString());
      xmlOutput.writeEndElement();
    }

    {
      xmlOutput.writeStartElement(WIX_NAMESPACE, "Property");
      xmlOutput.writeAttribute("Id", "ARPPRODUCTICON");
      xmlOutput.writeAttribute("Value", "Icon.ico");
      xmlOutput.writeEndElement();
    }

    /*
     * Embed all the data directly into the MSI file.
     */

    {
      xmlOutput.writeStartElement(WIX_NAMESPACE, "MediaTemplate");
      xmlOutput.writeAttribute("EmbedCab", "yes");
      xmlOutput.writeEndElement();
    }

    {
      xmlOutput.writeStartElement(WIX_NAMESPACE, "StandardDirectory");
      xmlOutput.writeAttribute("Id", "ProgramFilesFolder");

      {
        xmlOutput.writeStartElement(WIX_NAMESPACE, "Directory");
        xmlOutput.writeAttribute("Id", "CompanyFolder");
        xmlOutput.writeAttribute("Name", "io7m");

        {
          xmlOutput.writeStartElement(WIX_NAMESPACE, "Directory");
          xmlOutput.writeAttribute("Id", "INSTALLLOCATION");
          xmlOutput.writeAttribute("Name", this.appLongName);
          xmlOutput.writeEndElement();
        }

        xmlOutput.writeEndElement();
      }

      xmlOutput.writeEndElement();
    }

    {
      xmlOutput.writeStartElement(WIX_NAMESPACE, "Feature");
      xmlOutput.writeAttribute("Id", "Application");
      xmlOutput.writeAttribute("Title", "Application");
      xmlOutput.writeAttribute("Level", "1");
      xmlOutput.writeAttribute("ConfigurableDirectory", "INSTALLLOCATION");

      {
        xmlOutput.writeStartElement(WIX_NAMESPACE, "ComponentGroupRef");
        xmlOutput.writeAttribute("Id", "Files");
        xmlOutput.writeEndElement();
      }

      xmlOutput.writeEndElement();
    }

    xmlOutput.writeEndElement();
  }

  @Override
  public Void call()
    throws Exception
  {
    final var xmlOutputs = XMLOutputFactory.newFactory();
    try (var outputWriter = Files.newBufferedWriter(
      this.outputWixFile,
      UTF_8,
      CREATE,
      TRUNCATE_EXISTING)) {
      final var xmlOutput = xmlOutputs.createXMLStreamWriter(outputWriter);

      xmlOutput.writeStartDocument("UTF-8", "1.0");
      outputWriter.newLine();

      xmlOutput.setDefaultNamespace(WIX_NAMESPACE);
      xmlOutput.writeStartElement(WIX_NAMESPACE, "Wix");
      xmlOutput.writeAttribute("xmlns", WIX_NAMESPACE);
      this.writePackage(xmlOutput);
      this.writeFilesFragment(xmlOutput);
      xmlOutput.writeEndElement();
      xmlOutput.writeEndDocument();
    }
    return null;
  }

  private void writeFilesFragment(final XMLStreamWriter xmlOutput)
    throws Exception
  {
    xmlOutput.writeStartElement(WIX_NAMESPACE, "Fragment");

    xmlOutput.writeStartElement(WIX_NAMESPACE, "ComponentGroup");
    xmlOutput.writeAttribute("Id", "Files");

    {
      final var files = Files.walk(this.inputDistribution).sorted().toList();
      for (final var file : files) {
        if (!Files.isRegularFile(file)) {
          continue;
        }

        xmlOutput.writeStartElement(WIX_NAMESPACE, "Component");
        xmlOutput.writeAttribute("Directory", "INSTALLLOCATION");

        final var subdirectory = this.inputDistribution.relativize(file);
        if (subdirectory.getNameCount() > 1) {
          xmlOutput.writeAttribute(
            "Subdirectory",
            subdirectory.getParent().toString());
        }

        xmlOutput.writeStartElement(WIX_NAMESPACE, "File");
        xmlOutput.writeAttribute("Source", file.toString());
        xmlOutput.writeAttribute("KeyPath", "yes");
        xmlOutput.writeEndElement();

        xmlOutput.writeEndElement();
      }
    }

    xmlOutput.writeEndElement();
    xmlOutput.writeEndElement();
  }
}
