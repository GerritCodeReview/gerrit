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

package com.google.gerrit.server.config;

import com.google.common.flogger.FluentLogger;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

@Singleton
public class FileBasedGlobalPluginConfigProvider implements GlobalPluginConfigProvider {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final SitePaths site;

  @Inject
  FileBasedGlobalPluginConfigProvider(SitePaths site) {
    this.site = site;
  }

  @Override
  public Config get(String pluginName) {
    Path pluginConfigFile = site.etc_dir.resolve(pluginName + ".config");
    FileBasedConfig cfg = new FileBasedConfig(pluginConfigFile.toFile(), FS.DETECTED);
    if (!cfg.getFile().exists()) {
      logger.atInfo().log("No %s; assuming defaults", pluginConfigFile.toAbsolutePath());
      return cfg;
    }

    try {
      cfg.load();
    } catch (ConfigInvalidException e) {
      // This is an error in user input, don't spam logs with a stack trace.
      logger.atWarning().log(
          "Failed to load %s: %s", pluginConfigFile.toAbsolutePath(), e.getMessage());
    } catch (IOException e) {
      logger.atWarning().withCause(e).log("Failed to load %s", pluginConfigFile.toAbsolutePath());
    }
    return cfg;
  }
}
