// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.git;

import static com.google.inject.Scopes.SINGLETON;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import java.io.IOException;
import java.nio.file.Path;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

public class JGitConfigProvider implements Provider<FileBasedConfig> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static Module module() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        bind(Config.class)
            .annotatedWith(JGitConfig.class)
            .toProvider(JGitConfigProvider.class)
            .in(SINGLETON);
      }
    };
  }

  private final SitePaths site;

  @Inject
  JGitConfigProvider(SitePaths site) {
    this.site = site;
  }

  @Override
  public FileBasedConfig get() {
    FileBasedConfig cfg = loadConfig(site.jgit_config);
    if (!cfg.getFile().exists()) {
      logger.atInfo().log("No %s; assuming defaults", site.jgit_config);
    }
    return cfg;
  }

  private static FileBasedConfig loadConfig(Path path) {
    FileBasedConfig cfg = new FileBasedConfig(path.toFile(), FS.DETECTED);
    try {
      cfg.load();
    } catch (IOException | ConfigInvalidException e) {
      throw new ProvisionException(e.getMessage(), e);
    }
    return cfg;
  }
}
