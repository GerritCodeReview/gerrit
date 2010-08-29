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

import static com.google.gerrit.pgm.init.InitUtil.die;
import static com.google.gerrit.pgm.init.InitUtil.username;

import com.google.gerrit.launcher.GerritLauncher;
import com.google.gerrit.pgm.util.ConsoleUI;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.storage.file.LockFile;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;

/** Initialize the {@code container} configuration section. */
@Singleton
class InitContainer implements InitStep {
  private final ConsoleUI ui;
  private final SitePaths site;
  private final Section container;

  @Inject
  InitContainer(final ConsoleUI ui, final SitePaths site,
      final Section.Factory sections) {
    this.ui = ui;
    this.site = site;
    this.container = sections.get("container");
  }

  public void run() throws FileNotFoundException, IOException {
    ui.header("Container Process");

    container.string("Run as", "user", username());
    container.string("Java runtime", "javaHome", javaHome());

    File myWar;
    try {
      myWar = GerritLauncher.getDistributionArchive();
    } catch (FileNotFoundException e) {
      System.err.println("warn: Cannot find gerrit.war");
      myWar = null;
    }

    String path = container.get("war");
    if (path != null) {
      path = container.string("Gerrit runtime", "war", //
          myWar != null ? myWar.getAbsolutePath() : null);
      if (path == null || path.isEmpty()) {
        throw die("container.war is required");
      }

    } else if (myWar != null) {
      final boolean copy;
      final File siteWar = site.gerrit_war;
      if (siteWar.exists()) {
        copy = ui.yesno(true, "Upgrade %s", siteWar.getPath());
      } else {
        copy = ui.yesno(true, "Copy gerrit.war to %s", siteWar.getPath());
        if (copy) {
          container.unset("war");
        } else {
          container.set("war", myWar.getAbsolutePath());
        }
      }
      if (copy) {
        if (!ui.isBatch()) {
          System.err.format("Copying gerrit.war to %s", siteWar.getPath());
          System.err.println();
        }

        FileInputStream in = new FileInputStream(myWar);
        try {
          siteWar.getParentFile().mkdirs();

          LockFile lf = new LockFile(siteWar, FS.DETECTED);
          if (!lf.lock()) {
            throw new IOException("Cannot lock " + siteWar);
          }

          try {
            final OutputStream out = lf.getOutputStream();
            try {
              final byte[] tmp = new byte[4096];
              for (;;) {
                int n = in.read(tmp);
                if (n < 0) {
                  break;
                }
                out.write(tmp, 0, n);
              }
            } finally {
              out.close();
            }
            if (!lf.commit()) {
              throw new IOException("Cannot commit " + siteWar);
            }
          } finally {
            lf.unlock();
          }
        } finally {
          in.close();
        }
      }
    }
  }

  private static String javaHome() {
    return System.getProperty("java.home");
  }
}
