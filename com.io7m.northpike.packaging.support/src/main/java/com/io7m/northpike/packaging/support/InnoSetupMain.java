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

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Run {@code InnoSetup} to produce an app installer.
 */

public final class InnoSetupMain
{
  private static final Logger LOG =
    LoggerFactory.getLogger(InnoSetupMain.class);

  private InnoSetupMain()
  {

  }

  private enum ArchiveType {
    TGZ,
    ZIP
  }

  /**
   * Run {@code InnoSetup}.
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
  }
}
