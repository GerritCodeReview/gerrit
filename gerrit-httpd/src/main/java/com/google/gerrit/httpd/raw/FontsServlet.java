// Copyright (C) 2016 The Android Open Source Project
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
import com.google.gerrit.launcher.GerritLauncher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class FontsServlet extends ResourceServlet {
  private static final long serialVersionUID = 1L;

  private final Path zip;
  private final Path fonts;

  FontsServlet(Cache<Path, Resource> cache, Path buckOut)
      throws IOException {
    super(cache, true);
    zip = getZipPath(buckOut);
    if (zip == null || !Files.exists(zip)) {
      fonts = null;
    } else {
      fonts = GerritLauncher
          .newZipFileSystem(zip)
          .getPath("/");
    }
  }

  @Override
  protected Path getResourcePath(String pathInfo) throws IOException {
    if (fonts == null) {
      throw new IOException("No fonts found: " + zip
          + ". Run `buck build //polygerrit-ui:fonts`?");
    }
    return fonts.resolve(pathInfo);
  }

  private static Path getZipPath(Path buckOut) {
    if (buckOut == null) {
      return null;
    }
    return buckOut.resolve("gen")
        .resolve("polygerrit-ui")
        .resolve("fonts")
        .resolve("fonts.zip");
  }
}
