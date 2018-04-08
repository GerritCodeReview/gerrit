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

import com.google.gerrit.common.FileUtil;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.pgm.init.api.Section;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Initialize the {@code cache} configuration section. */
@Singleton
class InitCache implements InitStep {
  private final ConsoleUI ui;
  private final InitFlags flags;
  private final SitePaths site;
  private final Section cache;

  @Inject
  InitCache(
      final ConsoleUI ui,
      final InitFlags flags,
      final SitePaths site,
      final Section.Factory sections) {
    this.ui = ui;
    this.flags = flags;
    this.site = site;
    this.cache = sections.get("cache", null);
  }

  @Override
  public void run() {
    ui.header("Cache");
    String path = cache.get("directory");

    if (path != null && path.isEmpty()) {
      // Explicitly set to empty implies the administrator has
      // disabled the on disk cache and doesn't want it enabled.
      //
      return;
    }

    if (path == null) {
      path = "cache";
      cache.set("directory", path);
    }

    Path loc = site.resolve(path);
    FileUtil.mkdirsOrDie(loc, "cannot create cache.directory");
    List<Path> cacheFiles = new ArrayList<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(loc, "*.{lock,h2,trace}.db")) {
      for (Path entry : stream) {
        cacheFiles.add(entry);
      }
    } catch (IOException e) {
      ui.message("IO error during cache directory scan");
      return;
    }
    if (!cacheFiles.isEmpty()) {
      for (Path entry : cacheFiles) {
        if (flags.deleteCaches || ui.yesno(false, "Delete cache file %s", entry)) {
          try {
            Files.deleteIfExists(entry);
          } catch (IOException e) {
            ui.message("Could not delete " + entry);
          }
        }
      }
    }
  }
}
