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
import com.google.gerrit.launcher.GerritLauncher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class BowerComponentsServlet extends ResourceServlet {
  private static final long serialVersionUID = 1L;

  private final Path zip;
  private final Path bowerComponents;

  BowerComponentsServlet(Cache<Path, Resource> cache, Path buckOut) throws IOException {
    super(cache, true);
    zip = getZipPath(buckOut);
    if (zip == null || !Files.exists(zip)) {
      bowerComponents = null;
    } else {
      bowerComponents = GerritLauncher.newZipFileSystem(zip).getPath("/");
    }
  }

  @Override
  protected Path getResourcePath(String pathInfo) throws IOException {
    if (bowerComponents == null) {
      throw new IOException(
          "No polymer components found: "
              + zip
              + ". Run `buck build //polygerrit-ui:polygerrit_components`?");
    }
    return bowerComponents.resolve(pathInfo);
  }

  private static Path getZipPath(Path buckOut) {
    if (buckOut == null) {
      return null;
    }
    return buckOut
        .resolve("gen")
        .resolve("polygerrit-ui")
        .resolve("polygerrit_components")
        .resolve("polygerrit_components.bower_components.zip");
  }
}
