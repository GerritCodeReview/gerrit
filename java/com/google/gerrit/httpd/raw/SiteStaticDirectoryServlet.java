// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.httpd.raw;

import com.google.common.cache.Cache;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.nio.file.Path;
import org.eclipse.jgit.lib.Config;

/** Sends static content from the site 's {@code static/} subdirectory. */
@Singleton
public class SiteStaticDirectoryServlet extends ResourceServlet {
  private static final long serialVersionUID = 1L;

  private final Path staticBase;

  @Inject
  SiteStaticDirectoryServlet(
      SitePaths site,
      @GerritServerConfig Config cfg,
      @Named(StaticModule.CACHE) Cache<Path, Resource> cache) {
    super(cache, cfg.getBoolean("site", "refreshHeaderFooter", true));
    Path p;
    try {
      p = site.static_dir.toRealPath().normalize();
    } catch (IOException e) {
      p = site.static_dir.toAbsolutePath().normalize();
    }
    staticBase = p;
  }

  @Override
  protected Path getResourcePath(String pathInfo) {
    return staticBase.resolve(pathInfo);
  }
}
