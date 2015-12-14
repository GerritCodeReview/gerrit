// Copyright (C) 2015 The Android Open Source Project
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

import static java.nio.file.Files.exists;
import static java.nio.file.Files.isReadable;

import com.google.common.cache.Cache;
import com.google.gerrit.httpd.raw.ResourceServlet.Resource;
import com.google.gerrit.launcher.GerritLauncher;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.servlet.ServletModule;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class StaticModule extends ServletModule {
  private static final Logger log =
      LoggerFactory.getLogger(StaticModule.class);

  private static final String DOC_SERVLET = "DocServlet";
  private static final String FAVICON_SERVLET = "FaviconServlet";
  private static final String GWT_UI_SERVLET = "GwtUiServlet";
  private static final String ROBOTS_TXT_SERVLET = "RobotsTxtServlet";

  static final String CACHE = "static_content";

  private final FileSystem warFs;
  private final Path buckOut;
  private final Path unpackedWar;

  public StaticModule() {
    warFs = getDistributionArchive();
    if (warFs == null) {
      buckOut = getDeveloperBuckOut();
      unpackedWar = makeWarTempDir();
    } else {
      buckOut = null;
      unpackedWar = null;
    }
  }

  @Override
  protected void configureServlets() {
    serveRegex("^/Documentation/(.+)$").with(named(DOC_SERVLET));
    serve("/static/*").with(SiteStaticDirectoryServlet.class);
    serve("/robots.txt").with(named(ROBOTS_TXT_SERVLET));
    serve("/favicon.ico").with(named(FAVICON_SERVLET));
    serveGwtUi();
    install(new CacheModule() {
      @Override
      protected void configure() {
        cache(CACHE, Path.class, Resource.class)
            .maximumWeight(1 << 20)
            .weigher(ResourceServlet.Weigher.class);
      }
    });
  }

  private void serveGwtUi() {
    serveRegex("^/gerrit_ui/(?!rpc/)(.*)$")
        .with(Key.get(HttpServlet.class, Names.named(GWT_UI_SERVLET)));
    if (warFs == null) {
      filter("/").through(new RecompileGwtUiFilter(buckOut, unpackedWar));
    }
  }

  @Provides
  @Singleton
  @Named(DOC_SERVLET)
  HttpServlet getDocServlet(@Named(CACHE) Cache<Path, Resource> cache) {
    if (warFs != null) {
      return new WarDocServlet(cache, warFs);
    } else {
      return new HttpServlet() {
        private static final long serialVersionUID = 1L;

        @Override
        protected void service(HttpServletRequest req,
            HttpServletResponse resp) throws IOException {
          resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
      };
    }
  }

  @Provides
  @Singleton
  @Named(GWT_UI_SERVLET)
  HttpServlet getGwtUiServlet(@Named(CACHE) Cache<Path, Resource> cache)
      throws IOException {
    if (warFs != null) {
      return new WarGwtUiServlet(cache, warFs);
    } else {
      return new DeveloperGwtUiServlet(cache, unpackedWar);
    }
  }

  @Provides
  @Singleton
  @Named(ROBOTS_TXT_SERVLET)
  HttpServlet getRobotsTxtServlet(@GerritServerConfig Config cfg,
      SitePaths sitePaths, @Named(CACHE) Cache<Path, Resource> cache) {
    Path configPath = sitePaths.resolve(
        cfg.getString("httpd", null, "robotsFile"));
    if (configPath != null) {
      if (exists(configPath) && isReadable(configPath)) {
        return new SingleFileServlet(cache, configPath, true);
      } else {
        log.warn("Cannot read httpd.robotsFile, using default");
      }
    }
    if (warFs != null) {
      return new SingleFileServlet(cache, warFs.getPath("/robots.txt"), false);
    } else {
      return new SingleFileServlet(cache, webappSourcePath("robots.txt"), true);
    }
  }

  @Provides
  @Singleton
  @Named(FAVICON_SERVLET)
  HttpServlet getFaviconServlet(@Named(CACHE) Cache<Path, Resource> cache) {
    if (warFs != null) {
      return new SingleFileServlet(cache, warFs.getPath("/favicon.ico"), false);
    } else {
      return new SingleFileServlet(
          cache, webappSourcePath("favicon.ico"), true);
    }
  }

  private Path webappSourcePath(String name) {
    return buckOut.resolveSibling("gerrit-war").resolve("src").resolve("main")
        .resolve("webapp").resolve(name);
  }

  private static Key<HttpServlet> named(String name) {
    return Key.get(HttpServlet.class, Names.named(name));
  }

  private static FileSystem getDistributionArchive() {
    try {
      return GerritLauncher.getDistributionArchiveFileSystem();
    } catch (IOException e) {
      if ((e instanceof FileNotFoundException)
          && GerritLauncher.NOT_ARCHIVED.equals(e.getMessage())) {
        return null;
      } else {
        ProvisionException pe =
            new ProvisionException("Error reading gerrit.war");
        pe.initCause(e);
        throw pe;
      }
    }
  }

  private static Path getDeveloperBuckOut() {
    try {
      return GerritLauncher.getDeveloperBuckOut();
    } catch (FileNotFoundException e) {
      return null;
    }
  }

  private static Path makeWarTempDir() {
    // Obtain our local temporary directory, but it comes back as a file
    // so we have to switch it to be a directory post creation.
    //
    try {
      File dstwar = GerritLauncher.createTempFile("gerrit_", "war");
      if (!dstwar.delete() || !dstwar.mkdir()) {
        throw new IOException("Cannot mkdir " + dstwar.getAbsolutePath());
      }

      // Jetty normally refuses to serve out of a symlinked directory, as
      // a security feature. Try to resolve out any symlinks in the path.
      //
      try {
        return dstwar.getCanonicalFile().toPath();
      } catch (IOException e) {
        return dstwar.getAbsoluteFile().toPath();
      }
    } catch (IOException e) {
      ProvisionException pe =
          new ProvisionException("Cannot create war tempdir");
      pe.initCause(e);
      throw pe;
    }
  }
}
