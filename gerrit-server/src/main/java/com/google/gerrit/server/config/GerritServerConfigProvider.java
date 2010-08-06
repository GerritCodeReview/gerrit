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

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/** Provides {@link Config} annotated with {@link GerritServerConfig}. */
class GerritServerConfigProvider implements Provider<Config> {
  private static final Logger log =
      LoggerFactory.getLogger(GerritServerConfigProvider.class);

  private final SitePaths site;

  @Inject
  GerritServerConfigProvider(final SitePaths site) {
    this.site = site;
  }

  @Override
  public Config get() {
    FileBasedConfig cfg = new FileBasedConfig(site.gerrit_config, FS.DETECTED);

    if (!cfg.getFile().exists()) {
      log.info("No " + site.gerrit_config.getAbsolutePath()
          + "; assuming defaults");
      return cfg;
    }

    try {
      cfg.load();
    } catch (IOException e) {
      throw new ProvisionException(e.getMessage(), e);
    } catch (ConfigInvalidException e) {
      throw new ProvisionException(e.getMessage(), e);
    }

    if (site.secure_config.exists()) {
      cfg = new FileBasedConfig(cfg, site.secure_config, FS.DETECTED);
      try {
        cfg.load();
      } catch (IOException e) {
        throw new ProvisionException(e.getMessage(), e);
      } catch (ConfigInvalidException e) {
        throw new ProvisionException(e.getMessage(), e);
      }
    }

    return cfg;
  }
}
