// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.httpd.raw;

import com.google.common.cache.Cache;
import com.google.gerrit.common.TimeUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

class DirectoryGwtUiServlet extends ResourceServlet {
  private static final long serialVersionUID = 1L;

  private static final FileTime NOW = FileTime.fromMillis(TimeUtil.nowMs());

  private final Path ui;

  DirectoryGwtUiServlet(Cache<Path, Resource> cache, Path unpackedWar, boolean dev)
      throws IOException {
    super(cache, false);
    ui = unpackedWar.resolve("gerrit_ui");
    if (!Files.exists(ui)) {
      Files.createDirectory(ui);
    }
    if (dev) {
      ui.toFile().deleteOnExit();
    }
  }

  @Override
  protected Path getResourcePath(String pathInfo) {
    return ui.resolve(pathInfo);
  }

  @Override
  protected FileTime getLastModifiedTime(Path p) {
    // Return initialization time of this class, since the GWT outputs from the
    // build process all have mtimes of 1980/1/1.
    return NOW;
  }
}
