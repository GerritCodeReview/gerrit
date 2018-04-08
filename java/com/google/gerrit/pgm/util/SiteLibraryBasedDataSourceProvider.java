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

package com.google.gerrit.pgm.util;

import com.google.gerrit.common.SiteLibraryLoaderUtil;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.config.ThreadSettingsConfig;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.schema.DataSourceProvider;
import com.google.gerrit.server.schema.DataSourceType;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.eclipse.jgit.lib.Config;

/** Loads the site library if not yet loaded. */
@Singleton
public class SiteLibraryBasedDataSourceProvider extends DataSourceProvider {
  private final Path libdir;
  private boolean init;

  @Inject
  SiteLibraryBasedDataSourceProvider(
      SitePaths site,
      @GerritServerConfig Config cfg,
      MetricMaker metrics,
      ThreadSettingsConfig tsc,
      DataSourceProvider.Context ctx,
      DataSourceType dst) {
    super(cfg, metrics, tsc, ctx, dst);
    libdir = site.lib_dir;
  }

  @Override
  public synchronized DataSource get() {
    if (!init) {
      SiteLibraryLoaderUtil.loadSiteLib(libdir);
      init = true;
    }
    return super.get();
  }
}
