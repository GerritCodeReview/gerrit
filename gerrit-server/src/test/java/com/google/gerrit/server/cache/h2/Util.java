// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.cache.h2;

import com.google.common.cache.Cache;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.server.cache.PersistentCacheFactory;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.testutil.TempFileUtil;

import org.eclipse.jgit.lib.Config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.ExecutorService;

public class Util {

  public static PersistentCacheFactory createH2Factory(
      DefaultCacheFactory factory, Config cfg) throws FileNotFoundException,
      IOException {
    return new H2CacheFactory(factory, cfg, createSitePath(),
        DynamicMap.<Cache<?, ?>> emptyMap());
  }

  private static SitePaths createSitePath() throws IOException,
      FileNotFoundException {
    File siteDir = TempFileUtil.createTempDirectory();
    return new SitePaths(siteDir.toPath());
  }

  public static ExecutorService getExecutorService(PersistentCacheFactory factory) {
    return ((H2CacheFactory)factory).getExecutorService();
  }
}
