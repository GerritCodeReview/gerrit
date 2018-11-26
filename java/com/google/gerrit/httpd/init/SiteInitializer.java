// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.httpd.init;

import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.pgm.init.BaseInit;
import com.google.gerrit.pgm.init.PluginsDistribution;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public final class SiteInitializer {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String GERRIT_SITE_PATH = "gerrit.site_path";

  private final String sitePath;
  private final String initPath;
  private final PluginsDistribution pluginsDistribution;
  private final List<String> pluginsToInstall;

  SiteInitializer(
      String sitePath,
      String initPath,
      PluginsDistribution pluginsDistribution,
      List<String> pluginsToInstall) {
    this.sitePath = sitePath;
    this.initPath = initPath;
    this.pluginsDistribution = pluginsDistribution;
    this.pluginsToInstall = pluginsToInstall;
  }

  public void init() {
    try {
      if (sitePath != null) {
        Path site = Paths.get(sitePath);
        logger.atInfo().log("Initializing site at %s", site.toRealPath().normalize());
        new BaseInit(site, false, pluginsDistribution, pluginsToInstall).run();
        return;
      }

      String path = System.getProperty(GERRIT_SITE_PATH);
      Path site = null;
      if (!Strings.isNullOrEmpty(path)) {
        site = Paths.get(path);
      }

      if (site == null && initPath != null) {
        site = Paths.get(initPath);
      }
      if (site != null) {
        logger.atInfo().log("Initializing site at %s", site.toRealPath().normalize());
        new BaseInit(site, false, pluginsDistribution, pluginsToInstall).run();
      }
    } catch (Exception e) {
      logger.atSevere().withCause(e).log("Site init failed");
      throw new RuntimeException(e);
    }
  }
}
