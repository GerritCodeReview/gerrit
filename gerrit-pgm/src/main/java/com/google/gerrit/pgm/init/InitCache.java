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
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.pgm.init.api.Section;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.nio.file.Path;

/** Initialize the {@code cache} configuration section. */
@Singleton
class InitCache implements InitStep {
  private final SitePaths site;
  private final Section cache;

  @Inject
  InitCache(final SitePaths site, final Section.Factory sections) {
    this.site = site;
    this.cache = sections.get("cache", null);
  }

  @Override
  public void run() {
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
  }
}
