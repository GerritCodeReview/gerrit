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

import static com.google.common.base.Preconditions.checkArgument;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.isReadable;

import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.httpd.GerritOptions;
import com.google.gerrit.httpd.XsrfCookieFilter;
import com.google.gerrit.httpd.raw.ResourceServlet.Resource;
import com.google.gerrit.launcher.GerritLauncher;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
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

  public static final String CACHE = "static_content";

  public static final ImmutableList<String> POLYGERRIT_INDEX_PATHS =
      ImmutableList.of(
        "/",
        "/c/*",
        "/q/*",
        "/x/*",
        "/admin/*",
        "/dashboard/*",
        "/settings/*",
        // TODO(dborowitz): These fragments conflict with the REST API
        // namespace, so they will need to use a different path.
        "/groups/*",
        "/projects/*");

  private static final String DOC_SERVLET = "DocServlet";
  private static final String FAVICON_SERVLET = "FaviconServlet";
  private static final String GWT_UI_SERVLET = "GwtUiServlet";
  private static final String POLYGERRIT_INDEX_SERVLET =
      "PolyGerritUiIndexServlet";
  private static final String ROBOTS_TXT_SERVLET = "RobotsTxtServlet";

  private final GerritOptions options;
  private Paths paths;

  @Inject
  public StaticModule(GerritOptions options) {
    this.options = options;
  }

  private Paths getPaths() {
    if (paths == null) {
      paths = new Paths();
    }
    return paths;
  }

  @Override
  protected void configureServlets() {
    serveRegex("^/Documentation/(.+)$").with(named(DOC_SERVLET));
    serve("/static/*").with(SiteStaticDirectoryServlet.class);
    install(new CacheModule() {
      @Override
      protected void configure() {
        cache(CACHE, Path.class, Resource.class)
            .maximumWeight(1 << 20)
            .weigher(ResourceServlet.Weigher.class);
      }
    });
    if (options.enablePolyGerrit()) {
      install(new CoreStaticModule());
      install(new PolyGerritUiModule());
    } else if (options.enableDefaultUi()) {
      install(new CoreStaticModule());
      install(new GwtUiModule());
    }
  }

  @Provides
  @Singleton
  @Named(DOC_SERVLET)
  HttpServlet getDocServlet(@Named(CACHE) Cache<Path, Resource> cache) {
    Paths p = getPaths();
    if (p.warFs != null) {
      return new WarDocServlet(cache, p.warFs);
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

  private class CoreStaticModule extends ServletModule {
    @Override
    public void configureServlets() {
      serve("/robots.txt").with(named(ROBOTS_TXT_SERVLET));
      serve("/favicon.ico").with(named(FAVICON_SERVLET));
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
      Paths p = getPaths();
      if (p.warFs != null) {
        return new SingleFileServlet(
            cache, p.warFs.getPath("/robots.txt"), false);
      } else {
        return new SingleFileServlet(
            cache, webappSourcePath("robots.txt"), true);
      }
    }

    @Provides
    @Singleton
    @Named(FAVICON_SERVLET)
    HttpServlet getFaviconServlet(@Named(CACHE) Cache<Path, Resource> cache) {
      Paths p = getPaths();
      if (p.warFs != null) {
        return new SingleFileServlet(
            cache, p.warFs.getPath("/favicon.ico"), false);
      } else {
        return new SingleFileServlet(
            cache, webappSourcePath("favicon.ico"), true);
      }
    }

    private Path webappSourcePath(String name) {
      return getPaths().buckOut.resolveSibling("gerrit-war").resolve("src")
          .resolve("main").resolve("webapp").resolve(name);
    }
  }

  private class GwtUiModule extends ServletModule {
    @Override
    public void configureServlets() {
      serveRegex("^/gerrit_ui/(?!rpc/)(.*)$")
          .with(Key.get(HttpServlet.class, Names.named(GWT_UI_SERVLET)));
      Paths p = getPaths();
      if (p.warFs == null && p.buckOut != null) {
        filter("/").through(new RecompileGwtUiFilter(p.buckOut, p.unpackedWar));
      }
    }

    @Provides
    @Singleton
    @Named(GWT_UI_SERVLET)
    HttpServlet getGwtUiServlet(@Named(CACHE) Cache<Path, Resource> cache)
        throws IOException {
      Paths p = getPaths();
      if (p.warFs != null) {
        return new WarGwtUiServlet(cache, p.warFs);
      } else {
        return new DeveloperGwtUiServlet(cache, p.unpackedWar);
      }
    }
  }

  private class PolyGerritUiModule extends ServletModule {
    @Override
    public void configureServlets() {
      Path buckOut = getPaths().buckOut;
      if (buckOut != null) {
        RebuildBowerComponentsFilter rebuildFilter =
            new RebuildBowerComponentsFilter(buckOut);
        for (String p : POLYGERRIT_INDEX_PATHS) {
          // Rebuilding bower_components once per load on the index request,
          // is sufficient, since it will finish building before attempting to
          // access any bower_components resources. Plus it saves contention and
          // extraneous buck builds.
          filter(p).through(rebuildFilter);
        }
        serve("/bower_components/*").with(BowerComponentsServlet.class);
      } else {
        // In the war case, bower_components are either inlined by vulcanize, or
        // live under /polygerrit_ui in the war file, so we don't need a
        // separate servlet.
      }

      Key<HttpServlet> indexKey = named(POLYGERRIT_INDEX_SERVLET);
      for (String p : POLYGERRIT_INDEX_PATHS) {
        filter(p).through(XsrfCookieFilter.class);
        serve(p).with(indexKey);
      }
      serve("/*").with(PolyGerritUiServlet.class);
    }

    @Provides
    @Singleton
    @Named(POLYGERRIT_INDEX_SERVLET)
    HttpServlet getPolyGerritUiIndexServlet(
        @Named(CACHE) Cache<Path, Resource> cache) {
      return new SingleFileServlet(
          cache, polyGerritBasePath().resolve("index.html"), isDev());
    }

    @Provides
    @Singleton
    PolyGerritUiServlet getPolyGerritUiServlet(
        @Named(CACHE) Cache<Path, Resource> cache) {
      return new PolyGerritUiServlet(cache, polyGerritBasePath());
    }

    @Provides
    @Singleton
    BowerComponentsServlet getBowerComponentsServlet(
        @Named(CACHE) Cache<Path, Resource> cache) {
      return new BowerComponentsServlet(cache, getPaths().buckOut);
    }

    private boolean isDev() {
      return options.forcePolyGerritDev() || getPaths().warFs == null;
    }

    private Path polyGerritBasePath() {
      Paths p = getPaths();
      if (options.forcePolyGerritDev()) {
        checkArgument(p.buckOut != null,
            "no buck-out directory found for PolyGerrit developer mode");
      }
      return isDev()
          ? p.buckOut.getParent().resolve("polygerrit-ui").resolve("app")
          : p.warFs.getPath("/polygerrit_ui");
    }
  }

  private class Paths {
    private final FileSystem warFs;
    private final Path buckOut;
    private final Path unpackedWar;

    private Paths() {
      try {
        warFs = getDistributionArchive();
        if (warFs == null) {
          buckOut = getDeveloperBuckOut();
          unpackedWar = makeWarTempDir();
        } else if (options.forcePolyGerritDev()) {
          buckOut = getDeveloperBuckOut();
          unpackedWar = null;
        } else {
          buckOut = null;
          unpackedWar = null;
        }
      } catch (IOException e) {
        throw new ProvisionException(
            "Error initializing static content paths", e);
      }
    }

    private FileSystem getDistributionArchive() throws IOException {
      File war;
      try {
        war = GerritLauncher.getDistributionArchive();
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
      return GerritLauncher.getZipFileSystem(war.toPath());
    }

    private Path getDeveloperBuckOut() {
      try {
        return GerritLauncher.getDeveloperBuckOut();
      } catch (FileNotFoundException e) {
        return null;
      }
    }

    private Path makeWarTempDir() {
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

  private static Key<HttpServlet> named(String name) {
    return Key.get(HttpServlet.class, Names.named(name));
  }
}
