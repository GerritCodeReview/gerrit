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
import com.google.gerrit.server.util.time.TimeUtil;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

class PolyGerritUiServlet extends ResourceServlet {
  private static final long serialVersionUID = 1L;

  private static final FileTime NOW = FileTime.fromMillis(TimeUtil.nowMs());

  private final Path ui;

  PolyGerritUiServlet(Cache<Path, Resource> cache, Path ui) {
    super(cache, true);
    this.ui = ui;
  }

  @Override
  protected Path getResourcePath(String pathInfo) {
    return ui.resolve(pathInfo);
  }

  @Override
  protected FileTime getLastModifiedTime(Path p) throws IOException {
    if (ui.getFileSystem().equals(FileSystems.getDefault())) {
      // Assets are being served from disk, so we can trust the mtime.
      return super.getLastModifiedTime(p);
    }
    // Assume this FileSystem is serving from a WAR. All WAR outputs from the build process have
    // mtimes of 1980/1/1, so we can't trust it, and return the initialization time of this class
    // instead.
    return NOW;
  }
}
