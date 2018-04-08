// Copyright (C) 2017 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.gerrit.common.Die;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Singleton
public class StaleLibraryRemover {
  private final ConsoleUI ui;
  private final Path lib_dir;

  @Inject
  StaleLibraryRemover(ConsoleUI ui, SitePaths site) {
    this.ui = ui;
    this.lib_dir = site.lib_dir;
  }

  public void remove(String pattern) {
    if (!Strings.isNullOrEmpty(pattern)) {
      DirectoryStream.Filter<Path> filter =
          new DirectoryStream.Filter<Path>() {
            @Override
            public boolean accept(Path entry) {
              return entry.getFileName().toString().matches("^" + pattern + "$");
            }
          };
      try (DirectoryStream<Path> paths = Files.newDirectoryStream(lib_dir, filter)) {
        for (Path p : paths) {
          String old = p.getFileName().toString();
          String bak = "." + old + ".backup";
          Path dest = p.resolveSibling(bak);
          if (Files.exists(dest)) {
            ui.message("WARNING: not renaming %s to %s: already exists\n", old, bak);
            continue;
          }
          ui.message("Renaming %s to %s\n", old, bak);
          try {
            Files.move(p, dest);
          } catch (IOException e) {
            throw new Die("cannot rename " + old, e);
          }
        }
      } catch (IOException e) {
        throw new Die("cannot remove stale library versions", e);
      }
    }
  }
}
