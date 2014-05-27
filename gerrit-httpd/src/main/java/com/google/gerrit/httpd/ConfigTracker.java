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

package com.google.gerrit.httpd;

import com.google.gerrit.common.data.GerritConfig;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

import java.util.Date;

@Singleton
public class ConfigTracker {
  private final boolean refreshConfig;
  private final SitePaths site;

  @Inject
  ConfigTracker(SitePaths site, @GerritServerConfig Config cfg) {
    this.site = site;
    this.refreshConfig = cfg.getBoolean("site", "refreshConfig", false);
  }

  private long lastUpdated;

  public void setUpdated() {
    lastUpdated = (new Date()).getTime();
  }

  public boolean isStale(GerritConfig cfg) {
    return lastUpdated > cfg.getCreationTime()
        || (refreshConfig && site.gerrit_config.lastModified() > cfg.getCreationTime());
  }
}
