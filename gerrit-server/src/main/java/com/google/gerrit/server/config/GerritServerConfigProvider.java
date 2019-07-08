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

import com.google.gerrit.server.securestore.SecureStore;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Provides {@link Config} annotated with {@link GerritServerConfig}. */
class GerritServerConfigProvider implements Provider<Config> {
  private static final Logger log = LoggerFactory.getLogger(GerritServerConfigProvider.class);

  private final SitePaths site;
  private final SecureStore secureStore;

  @Inject
  GerritServerConfigProvider(SitePaths site, SecureStore secureStore) {
    this.site = site;
    this.secureStore = secureStore;
  }

  @Override
  public Config get() {
    FileBasedConfig cfg = new FileBasedConfig(site.gerrit_config.toFile(), FS.DETECTED);

    if (!cfg.getFile().exists()) {
      log.info("No " + site.gerrit_config.toAbsolutePath() + "; assuming defaults");
      return new GerritConfig(cfg, secureStore);
    }

    try {
      cfg.load();
    } catch (IOException | ConfigInvalidException e) {
      throw new ProvisionException(e.getMessage(), e);
    }

    return new GerritConfig(cfg, secureStore);
  }
}
