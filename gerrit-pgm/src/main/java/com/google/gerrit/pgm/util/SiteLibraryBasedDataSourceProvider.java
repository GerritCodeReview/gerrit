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

import com.google.common.primitives.Longs;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.schema.DataSourceProvider;
import com.google.gerrit.server.schema.DataSourceType;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Comparator;

import javax.sql.DataSource;

/** Loads the site library if not yet loaded. */
@Singleton
public class SiteLibraryBasedDataSourceProvider extends DataSourceProvider {
  private final File libdir;
  private boolean init;

  @Inject
  SiteLibraryBasedDataSourceProvider(SitePaths site,
      @GerritServerConfig Config cfg,
      DataSourceProvider.Context ctx,
      DataSourceType dst) {
    super(site, cfg, ctx, dst);
    libdir = site.lib_dir;
  }

  public synchronized DataSource get() {
    if (!init) {
      loadSiteLib();
      init = true;
    }
    return super.get();
  }

  private void loadSiteLib() {
    File[] jars = libdir.listFiles(new FileFilter() {
      @Override
      public boolean accept(File path) {
        String name = path.getName();
        return (name.endsWith(".jar") || name.endsWith(".zip"))
            && path.isFile();
      }
    });
    if (jars != null && 0 < jars.length) {
      Arrays.sort(jars, new Comparator<File>() {
        @Override
        public int compare(File a, File b) {
          // Sort by reverse last-modified time so newer JARs are first.
          int cmp = Longs.compare(b.lastModified(), a.lastModified());
          if (cmp != 0) {
            return cmp;
          }
          return a.getName().compareTo(b.getName());
        }
      });
      IoUtil.loadJARs(jars);
    }
  }
}
