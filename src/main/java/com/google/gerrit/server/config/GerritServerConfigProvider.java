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

package com.google.gerrit.server.config;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spearce.jgit.errors.ConfigInvalidException;
import org.spearce.jgit.lib.Config;
import org.spearce.jgit.lib.FileBasedConfig;
import org.spearce.jgit.lib.WindowCache;
import org.spearce.jgit.lib.WindowCacheConfig;

import java.io.File;
import java.io.IOException;

/** Provides {@link Config} annotated with {@link GerritServerConfig}. */
public class GerritServerConfigProvider implements Provider<Config> {
  private static final Logger log =
      LoggerFactory.getLogger(GerritServerConfigProvider.class);

  private final File sitePath;

  @Inject
  GerritServerConfigProvider(@SitePath final File path) {
    sitePath = path;
  }

  @Override
  public Config get() {
    final File cfgPath = new File(sitePath, "gerrit.config");
    final FileBasedConfig cfg = new FileBasedConfig(sitePath);

    if (!cfg.getFile().exists()) {
      log.info("No " + cfgPath.getAbsolutePath() + "; assuming defaults");
      return cfg;
    }

    try {
      cfg.load();
    } catch (IOException e) {
      throw new ProvisionException(e.getMessage(), e);
    } catch (ConfigInvalidException e) {
      throw new ProvisionException(e.getMessage(), e);
    }

    final WindowCacheConfig c = new WindowCacheConfig();
    c.fromConfig(cfg);
    WindowCache.reconfigure(c);

    return cfg;
  }
}
