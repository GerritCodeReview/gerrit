// Copyright (C) 2009 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.pgm.init;

import static com.google.gerrit.pgm.init.api.InitUtil.die;
import static com.google.gerrit.pgm.init.api.InitUtil.username;

import com.google.common.io.ByteStreams;
import com.google.gerrit.launcher.GerritLauncher;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.pgm.init.api.Section;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.internal.storage.file.LockFile;

/** Initialize the {@code container} configuration section. */
@Singleton
class InitContainer implements InitStep {
  private final ConsoleUI ui;
  private final SitePaths site;
  private final Section container;

  @Inject
  InitContainer(final ConsoleUI ui, final SitePaths site, final Section.Factory sections) {
    this.ui = ui;
    this.site = site;
    this.container = sections.get("container", null);
  }

  @Override
  public void run() throws FileNotFoundException, IOException {
    ui.header("Container Process");

    container.string("Run as", "user", username());
    container.string("Java runtime", "javaHome", javaHome());

    Path myWar;
    try {
      myWar = GerritLauncher.getDistributionArchive().toPath();
    } catch (FileNotFoundException e) {
      System.err.println("warn: Cannot find distribution archive (e.g. gerrit.war)");
      myWar = null;
    }

    String path = container.get("war");
    if (path != null) {
      path =
          container.string(
              "Gerrit runtime", "war", myWar != null ? myWar.toAbsolutePath().toString() : null);
      if (path == null || path.isEmpty()) {
        throw die("container.war is required");
      }

    } else if (myWar != null) {
      final boolean copy;
      final Path siteWar = site.gerrit_war;
      if (Files.exists(siteWar)) {
        copy = ui.yesno(true, "Upgrade %s", siteWar);
      } else {
        copy = ui.yesno(true, "Copy %s to %s", myWar.getFileName(), siteWar);
        if (copy) {
          container.unset("war");
        } else {
          container.set("war", myWar.toAbsolutePath().toString());
        }
      }
      if (copy) {
        if (!ui.isBatch()) {
          System.err.format("Copying %s to %s", myWar.getFileName(), siteWar);
          System.err.println();
        }

        try (InputStream in = Files.newInputStream(myWar)) {
          Files.createDirectories(siteWar.getParent());

          LockFile lf = new LockFile(siteWar.toFile());
          if (!lf.lock()) {
            throw new IOException("Cannot lock " + siteWar);
          }
          try {
            try (OutputStream out = lf.getOutputStream()) {
              ByteStreams.copy(in, out);
            }
            if (!lf.commit()) {
              throw new IOException("Cannot commit " + siteWar);
            }
          } finally {
            lf.unlock();
          }
        }
      }
    }
  }

  private static String javaHome() {
    return System.getProperty("java.home");
  }
}
