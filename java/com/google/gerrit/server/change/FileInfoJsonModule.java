// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.AbstractModule;
import org.eclipse.jgit.lib.Config;

public class FileInfoJsonModule extends AbstractModule {
  /** Use the new diff cache implementation {@link FileInfoJsonNewImpl}. */
  private final boolean useNewDiffCache;

  /** Used to dark launch the new diff cache with the list files endpoint. */
  private final boolean runNewDiffCacheAsync;

  public FileInfoJsonModule(@GerritServerConfig Config cfg) {
    this.useNewDiffCache =
        cfg.getBoolean("cache", "diff_cache", "runNewDiffCache_ListFiles", false);
    this.runNewDiffCacheAsync =
        cfg.getBoolean("cache", "diff_cache", "runNewDiffCacheAsync_listFiles", false);
  }

  @Override
  public void configure() {
    if (runNewDiffCacheAsync) {
      bind(FileInfoJson.class).to(FileInfoJsonComparingImpl.class);
      return;
    }
    bind(FileInfoJson.class)
        .to(useNewDiffCache ? FileInfoJsonNewImpl.class : FileInfoJsonOldImpl.class);
  }
}
