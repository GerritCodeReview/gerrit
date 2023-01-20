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
import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.httpd.XsrfCookieFilter;
import com.google.gerrit.httpd.raw.ResourceServlet.Resource;
import com.google.gerrit.launcher.GerritLauncher;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.GerritOptions;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.experiments.ExperimentFeatures;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.servlet.ServletModule;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.http.server.GitSmartHttpTools;
import org.eclipse.jgit.lib.Config;

public class StaticModule extends ServletModule {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String CACHE = "static_content";

  /**
   * Paths at which we should serve the main PolyGerrit application {@code index.html}.
   *
   * <p>Supports {@code "/*"} as a trailing wildcard.
   */
  public static final ImmutableList<String> POLYGERRIT_INDEX_PATHS =
      ImmutableList.of(
          "/",
          "/c/*",
          "/id/*",
          "/p/*",
          "/q/*",
          "/x/*",
          "/admin/*",
          "/dashboard/*",
          "/groups/self",
          "/settings/*",
          "/Documentation/q/*");

  /**
   * Paths that should be treated as static assets when serving PolyGerrit.
   *
   * <p>Supports {@code "/*"} as a trailing wildcard.
   */
  private static final ImmutableList<String> POLYGERRIT_ASSET_PATHS =
      ImmutableList.of(
          "/behaviors/*",
          "/bower_components/*",
          "/elements/*",
          "/fonts/*",
          "/scripts/*",
          "/styles/*",
          "/workers/*");

  private static final String DOC_SERVLET = "DocServlet";
  private static final String FAVICON_SERVLET = "FaviconServlet";
  private static final String SERVICE_WORKER_SERVLET = "ServiceWorkerServlet";
  private static final String POLYGERRIT_INDEX_SERVLET = "PolyGerritUiIndexServlet";
  private static final String ROBOTS_TXT_SERVLET = "RobotsTxtServlet";

  private final GerritOptions options;
  private Paths paths;

  @Inject
  public StaticModule(GerritOptions options) {
    this.options = options;
  }

  @Provides
  @Singleton
  private Paths getPaths() {
    if (paths == null) {
      paths = new Paths(options);
    }
    return paths;
  }

  @Override
  protected void configureServlets() {
    serveRegex("^/Documentation$").with(named(DOC_SERVLET));
    serveRegex("^/Documentation/$").with(named(DOC_SERVLET));
    serveRegex("^/Documentation/(.+)$").with(named(DOC_SERVLET));
    serve("/static/*").with(SiteStaticDirectoryServlet.class);
    install(
        new CacheModule() {
          @Override
          protected void configure() {
            cache(CACHE, Path.class, Resource.class)
                .maximumWeight(1 << 20)
                .weigher(ResourceServlet.Weigher.class);
          }
        });
    if (!options.headless()) {
      install(new CoreStaticModule());
      install(new PolyGerritModule());
    }
  }

  @Provides
  @Singleton
  @Named(DOC_SERVLET)
  HttpServlet getDocServlet(
      @Named(CACHE) Cache<Path, Resource> cache, ExperimentFeatures experimentFeatures) {
    Paths p = getPaths();
    if (p.warFs != null) {
      return new WarDocServlet(cache, p.warFs, experimentFeatures);
    } else if (p.unpackedWar != null && !p.isDev()) {
      return new DirectoryDocServlet(cache, p.unpackedWar, experimentFeatures);
    } else {
      return new HttpServlet() {
        private static final long serialVersionUID = 1L;

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
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
      serve("/service-worker.js").with(named(SERVICE_WORKER_SERVLET));
    }

    @Provides
    @Singleton
    @Named(ROBOTS_TXT_SERVLET)
    HttpServlet getRobotsTxtServlet(
        @GerritServerConfig Config cfg,
        SitePaths sitePaths,
        @Named(CACHE) Cache<Path, Resource> cache) {
      Path configPath = sitePaths.resolve(cfg.getString("httpd", null, "robotsFile"));
      if (configPath != null) {
        if (exists(configPath) && isReadable(configPath)) {
          return new SingleFileServlet(cache, configPath, true);
        }
        logger.atWarning().log("Cannot read httpd.robotsFile, using default");
      }
      Paths p = getPaths();
      if (p.warFs != null) {
        return new SingleFileServlet(cache, p.warFs.getPath("/robots.txt"), false);
      }
      return new SingleFileServlet(cache, webappSourcePath("robots.txt"), true);
    }

    @Provides
    @Singleton
    @Named(FAVICON_SERVLET)
    HttpServlet getFaviconServlet(@Named(CACHE) Cache<Path, Resource> cache) {
      Paths p = getPaths();
      if (p.warFs != null) {
        return new SingleFileServlet(cache, p.warFs.getPath("/favicon.ico"), false);
      }
      return new SingleFileServlet(cache, webappSourcePath("favicon.ico"), true);
    }

    @Provides
    @Singleton
    @Named(SERVICE_WORKER_SERVLET)
    HttpServlet getServiceWorkerServlet(@Named(CACHE) Cache<Path, Resource> cache) {
      Paths p = getPaths();
      if (p.warFs != null) {
        return new SingleFileServlet(
            cache, p.warFs.getPath("/polygerrit_ui/workers/service-worker.js"), false);
      }
      return new SingleFileServlet(
          cache, webappSourcePath("polygerrit_ui/workers/service-worker.js"), true);
    }

    private Path webappSourcePath(String name) {
      Paths p = getPaths();
      if (p.unpackedWar != null) {
        return p.unpackedWar.resolve(name);
      }
      return p.sourceRoot.resolve("webapp/" + name);
    }
  }

  private class PolyGerritModule extends ServletModule {
    @Override
    public void configureServlets() {
      for (String p : POLYGERRIT_INDEX_PATHS) {
        filter(p).through(XsrfCookieFilter.class);
      }
      filter("/*").through(PolyGerritFilter.class);
    }

    @Provides
    @Singleton
    @Named(POLYGERRIT_INDEX_SERVLET)
    HttpServlet getPolyGerritUiIndexServlet(
        @CanonicalWebUrl @Nullable String canonicalUrl,
        @GerritServerConfig Config cfg,
        GerritApi gerritApi,
        ExperimentFeatures experimentFeatures) {
      String cdnPath = options.devCdn().orElse(cfg.getString("gerrit", null, "cdnPath"));
      String faviconPath = cfg.getString("gerrit", null, "faviconPath");
      return new IndexServlet(canonicalUrl, cdnPath, faviconPath, gerritApi, experimentFeatures);
    }

    @Provides
    @Singleton
    PolyGerritUiServlet getPolyGerritUiServlet(@Named(CACHE) Cache<Path, Resource> cache) {
      return new PolyGerritUiServlet(cache, polyGerritBasePath());
    }

    private Path polyGerritBasePath() {
      Paths p = getPaths();

      return p.warFs != null
          ? p.warFs.getPath("/polygerrit_ui")
          : p.unpackedWar.resolve("polygerrit_ui");
    }
  }

  private static class Paths {
    private final FileSystem warFs;
    private final Path sourceRoot;
    private final Path unpackedWar;
    private final boolean development;

    private Paths(GerritOptions options) {
      try {
        File launcherLoadedFrom = getLauncherLoadedFrom();
        if (launcherLoadedFrom != null && launcherLoadedFrom.getName().endsWith(".jar")) {
          // Special case: unpacked war archive deployed in container.
          // The path is something like:
          // <container>/<gerrit>/WEB-INF/lib/launcher.jar
          // Switch to exploded war case with <container>/webapp>/<gerrit>
          // root directory
          warFs = null;
          unpackedWar =
              java.nio.file.Paths.get(
                  launcherLoadedFrom.getParentFile().getParentFile().getParentFile().toURI());
          sourceRoot = null;
          development = false;
          return;
        }
        warFs = getDistributionArchive(launcherLoadedFrom);
        if (warFs == null) {
          unpackedWar = makeWarTempDir();
          development = true;
        } else if (options.devCdn().isPresent()) {
          unpackedWar = null;
          development = true;
        } else {
          unpackedWar = null;
          development = false;
          sourceRoot = null;
          return;
        }
      } catch (IOException e) {
        throw new ProvisionException("Error initializing static content paths", e);
      }

      sourceRoot = getSourceRootOrNull();
    }

    @Nullable
    private static Path getSourceRootOrNull() {
      try {
        return GerritLauncher.resolveInSourceRoot(".");
      } catch (FileNotFoundException e) {
        return null;
      }
    }

    @Nullable
    private FileSystem getDistributionArchive(File war) throws IOException {
      if (war == null) {
        return null;
      }
      return GerritLauncher.getZipFileSystem(war.toPath());
    }

    @Nullable
    private File getLauncherLoadedFrom() {
      File war;
      try {
        war = GerritLauncher.getDistributionArchive();
      } catch (IOException e) {
        if ((e instanceof FileNotFoundException)
            && GerritLauncher.NOT_ARCHIVED.equals(e.getMessage())) {
          return null;
        }
        throw new ProvisionException("Error reading gerrit.war", e);
      }
      return war;
    }

    private boolean isDev() {
      return development;
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
        throw new ProvisionException("Cannot create war tempdir", e);
      }
    }
  }

  private static Key<HttpServlet> named(String name) {
    return Key.get(HttpServlet.class, Names.named(name));
  }

  @Singleton
  private static class PolyGerritFilter implements Filter {
    private final HttpServlet polyGerritIndex;
    private final PolyGerritUiServlet polygerritUI;

    @Inject
    PolyGerritFilter(
        @Named(POLYGERRIT_INDEX_SERVLET) HttpServlet polyGerritIndex,
        PolyGerritUiServlet polygerritUI) {
      this.polyGerritIndex = polyGerritIndex;
      this.polygerritUI = polygerritUI;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void destroy() {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
      HttpServletRequest req = (HttpServletRequest) request;
      HttpServletResponse res = (HttpServletResponse) response;

      if (!GitSmartHttpTools.isGitClient(req)) {
        GuiceFilterRequestWrapper reqWrapper = new GuiceFilterRequestWrapper(req);
        String path = pathInfo(req);

        if (isPolyGerritIndex(path)) {
          polyGerritIndex.service(reqWrapper, res);
          return;
        }
        if (isPolyGerritAsset(path)) {
          polygerritUI.service(reqWrapper, res);
          return;
        }
      }

      chain.doFilter(req, res);
    }

    private static String pathInfo(HttpServletRequest req) {
      String uri = req.getRequestURI();
      String ctx = req.getContextPath();
      return uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
    }

    private static boolean isPolyGerritAsset(String path) {
      return matchPath(POLYGERRIT_ASSET_PATHS, path);
    }

    private static boolean isPolyGerritIndex(String path) {
      return matchPath(POLYGERRIT_INDEX_PATHS, path);
    }

    private static boolean matchPath(Iterable<String> paths, String path) {
      for (String p : paths) {
        if (p.endsWith("/*")) {
          if (path.regionMatches(0, p, 0, p.length() - 1)) {
            return true;
          }
        } else if (p.equals(path)) {
          return true;
        }
      }
      return false;
    }
  }

  private static class GuiceFilterRequestWrapper extends HttpServletRequestWrapper {
    GuiceFilterRequestWrapper(HttpServletRequest req) {
      super(req);
    }

    @Nullable
    @Override
    public String getPathInfo() {
      String uri = getRequestURI();
      String ctx = getContextPath();
      // This is a workaround for long standing guice filter bug:
      // https://github.com/google/guice/issues/807
      String res = uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;

      // Match the logic in the ResourceServlet, that re-add "/"
      // for null path info
      if ("/".equals(res)) {
        return null;
      }
      return res;
    }
  }
}
