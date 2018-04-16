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
import static com.google.common.base.Preconditions.checkState;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.isReadable;

import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.UiType;
import com.google.gerrit.httpd.XsrfCookieFilter;
import com.google.gerrit.httpd.raw.ResourceServlet.Resource;
import com.google.gerrit.launcher.GerritLauncher;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.GerritOptions;
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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StaticModule extends ServletModule {
  private static final Logger log = LoggerFactory.getLogger(StaticModule.class);

  public static final String CACHE = "static_content";
  public static final String GERRIT_UI_COOKIE = "GERRIT_UI";

  /**
   * Paths at which we should serve the main PolyGerrit application {@code index.html}.
   *
   * <p>Supports {@code "/*"} as a trailing wildcard.
   */
  public static final ImmutableList<String> POLYGERRIT_INDEX_PATHS =
      ImmutableList.of(
          "/", "/c/*", "/p/*", "/q/*", "/x/*", "/admin/*", "/dashboard/*", "/settings/*");
  // TODO(dborowitz): These fragments conflict with the REST API
  // namespace, so they will need to use a different path.
  //"/groups/*",
  //"/projects/*");
  //

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
          "/styles/*");

  private static final String DOC_SERVLET = "DocServlet";
  private static final String FAVICON_SERVLET = "FaviconServlet";
  private static final String GWT_UI_SERVLET = "GwtUiServlet";
  private static final String POLYGERRIT_INDEX_SERVLET = "PolyGerritUiIndexServlet";
  private static final String ROBOTS_TXT_SERVLET = "RobotsTxtServlet";
  private static final String OPEN_SEARCH_SERVLET = "OpenSearchServlet";

  private static final int GERRIT_UI_COOKIE_MAX_AGE = 60 * 60 * 24 * 365;

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
    }
    if (options.enablePolyGerrit()) {
      install(new PolyGerritModule());
    }
    if (options.enableGwtUi()) {
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
    } else if (p.unpackedWar != null && !p.isDev()) {
      return new DirectoryDocServlet(cache, p.unpackedWar);
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
      serve("/opensearch.xml").with(named(OPEN_SEARCH_SERVLET));
      serve("/robots.txt").with(named(ROBOTS_TXT_SERVLET));
      serve("/favicon.ico").with(named(FAVICON_SERVLET));
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
        log.warn("Cannot read httpd.robotsFile, using default");
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

    private Path webappSourcePath(String name) {
      Paths p = getPaths();
      if (p.unpackedWar != null) {
        return p.unpackedWar.resolve(name);
      }
      return p.sourceRoot.resolve("webapp/" + name);
    }
  }

  private class GwtUiModule extends ServletModule {
    @Override
    public void configureServlets() {
      serveRegex("^/gerrit_ui/(?!rpc/)(.*)$")
          .with(Key.get(HttpServlet.class, Names.named(GWT_UI_SERVLET)));
      Paths p = getPaths();
      if (p.isDev()) {
        filter("/").through(new RecompileGwtUiFilter(p.builder, p.unpackedWar));
      }
    }

    @Provides
    @Singleton
    @Named(GWT_UI_SERVLET)
    HttpServlet getGwtUiServlet(@Named(CACHE) Cache<Path, Resource> cache) throws IOException {
      Paths p = getPaths();
      if (p.warFs != null) {
        return new WarGwtUiServlet(cache, p.warFs);
      }
      return new DirectoryGwtUiServlet(cache, p.unpackedWar, p.isDev());
    }
  }

  private class PolyGerritModule extends ServletModule {
    @Override
    public void configureServlets() {
      for (String p : POLYGERRIT_INDEX_PATHS) {
        // Skip XsrfCookieFilter for /, since that is already done in the GWT UI
        // path (UrlModule).
        if (!p.equals("/")) {
          filter(p).through(XsrfCookieFilter.class);
        }
      }
      filter("/*").through(PolyGerritFilter.class);
    }

    @Provides
    @Singleton
    @Named(OPEN_SEARCH_SERVLET)
    HttpServlet getOpenSearchServlet(
        @CanonicalWebUrl @Nullable String canonicalUrl, @GerritServerConfig Config cfg)
        throws URISyntaxException {
      String instanceName = cfg.getString("gerrit", null, "instanceName");
      return new OpenSearchServlet(canonicalUrl, instanceName);
    }

    @Provides
    @Singleton
    @Named(POLYGERRIT_INDEX_SERVLET)
    HttpServlet getPolyGerritUiIndexServlet(
        @CanonicalWebUrl @Nullable String canonicalUrl, @GerritServerConfig Config cfg)
        throws URISyntaxException {
      String cdnPath = cfg.getString("gerrit", null, "cdnPath");
      String faviconPath = cfg.getString("gerrit", null, "faviconPath");
      return new IndexServlet(canonicalUrl, cdnPath, faviconPath);
    }

    @Provides
    @Singleton
    PolyGerritUiServlet getPolyGerritUiServlet(@Named(CACHE) Cache<Path, Resource> cache) {
      return new PolyGerritUiServlet(cache, polyGerritBasePath());
    }

    @Provides
    @Singleton
    BowerComponentsDevServlet getBowerComponentsServlet(@Named(CACHE) Cache<Path, Resource> cache)
        throws IOException {
      return getPaths().isDev() ? new BowerComponentsDevServlet(cache, getPaths().builder) : null;
    }

    @Provides
    @Singleton
    FontsDevServlet getFontsServlet(@Named(CACHE) Cache<Path, Resource> cache) throws IOException {
      return getPaths().isDev() ? new FontsDevServlet(cache, getPaths().builder) : null;
    }

    private Path polyGerritBasePath() {
      Paths p = getPaths();
      if (options.forcePolyGerritDev()) {
        checkArgument(
            p.sourceRoot != null, "no source root directory found for PolyGerrit developer mode");
      }

      if (p.isDev()) {
        return p.sourceRoot.resolve("polygerrit-ui").resolve("app");
      }

      return p.warFs != null
          ? p.warFs.getPath("/polygerrit_ui")
          : p.unpackedWar.resolve("polygerrit_ui");
    }
  }

  private static class Paths {
    private final FileSystem warFs;
    private final BazelBuild builder;
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
          builder = null;
          return;
        }
        warFs = getDistributionArchive(launcherLoadedFrom);
        if (warFs == null) {
          unpackedWar = makeWarTempDir();
          development = true;
        } else if (options.forcePolyGerritDev()) {
          unpackedWar = null;
          development = true;
        } else {
          unpackedWar = null;
          development = false;
          sourceRoot = null;
          builder = null;
          return;
        }
      } catch (IOException e) {
        throw new ProvisionException("Error initializing static content paths", e);
      }

      sourceRoot = getSourceRootOrNull();
      builder = new BazelBuild(sourceRoot);
    }

    private static Path getSourceRootOrNull() {
      try {
        return GerritLauncher.resolveInSourceRoot(".");
      } catch (FileNotFoundException e) {
        return null;
      }
    }

    private FileSystem getDistributionArchive(File war) throws IOException {
      if (war == null) {
        return null;
      }
      return GerritLauncher.getZipFileSystem(war.toPath());
    }

    private File getLauncherLoadedFrom() {
      File war;
      try {
        war = GerritLauncher.getDistributionArchive();
      } catch (IOException e) {
        if ((e instanceof FileNotFoundException)
            && GerritLauncher.NOT_ARCHIVED.equals(e.getMessage())) {
          return null;
        }
        ProvisionException pe = new ProvisionException("Error reading gerrit.war");
        pe.initCause(e);
        throw pe;
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
        ProvisionException pe = new ProvisionException("Cannot create war tempdir");
        pe.initCause(e);
        throw pe;
      }
    }
  }

  private static Key<HttpServlet> named(String name) {
    return Key.get(HttpServlet.class, Names.named(name));
  }

  @Singleton
  private static class PolyGerritFilter implements Filter {
    private final GerritOptions options;
    private final Paths paths;
    private final HttpServlet polyGerritIndex;
    private final PolyGerritUiServlet polygerritUI;
    private final BowerComponentsDevServlet bowerComponentServlet;
    private final FontsDevServlet fontServlet;

    @Inject
    PolyGerritFilter(
        GerritOptions options,
        Paths paths,
        @Named(POLYGERRIT_INDEX_SERVLET) HttpServlet polyGerritIndex,
        PolyGerritUiServlet polygerritUI,
        @Nullable BowerComponentsDevServlet bowerComponentServlet,
        @Nullable FontsDevServlet fontServlet) {
      this.paths = paths;
      this.options = options;
      this.polyGerritIndex = polyGerritIndex;
      this.polygerritUI = polygerritUI;
      this.bowerComponentServlet = bowerComponentServlet;
      this.fontServlet = fontServlet;
      checkState(
          options.enablePolyGerrit(), "can't install PolyGerritFilter when PolyGerrit is disabled");
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
      if (handlePolyGerritParam(req, res)) {
        return;
      }
      if (!isPolyGerritEnabled(req)) {
        chain.doFilter(req, res);
        return;
      }

      GuiceFilterRequestWrapper reqWrapper = new GuiceFilterRequestWrapper(req);
      String path = pathInfo(req);

      // Special case assets during development that are built by Bazel and not
      // served out of the source tree.
      //
      // In the war case, these are either inlined by vulcanize, or live under
      // /polygerrit_ui in the war file, so we can just treat them as normal
      // assets.
      if (paths.isDev()) {
        if (path.startsWith("/bower_components/")) {
          bowerComponentServlet.service(reqWrapper, res);
          return;
        } else if (path.startsWith("/fonts/")) {
          fontServlet.service(reqWrapper, res);
          return;
        }
      }

      if (isPolyGerritIndex(path)) {
        polyGerritIndex.service(reqWrapper, res);
        return;
      }
      if (isPolyGerritAsset(path)) {
        polygerritUI.service(reqWrapper, res);
        return;
      }

      chain.doFilter(req, res);
    }

    private static String pathInfo(HttpServletRequest req) {
      String uri = req.getRequestURI();
      String ctx = req.getContextPath();
      return uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
    }

    private boolean handlePolyGerritParam(HttpServletRequest req, HttpServletResponse res)
        throws IOException {
      if (!options.enableGwtUi() || !"GET".equals(req.getMethod())) {
        return false;
      }
      boolean redirect = false;
      String param = req.getParameter("polygerrit");
      if ("1".equals(param)) {
        setPolyGerritCookie(req, res, UiType.POLYGERRIT);
        redirect = true;
      } else if ("0".equals(param)) {
        setPolyGerritCookie(req, res, UiType.GWT);
        redirect = true;
      }
      if (redirect) {
        // Strip polygerrit param from URL. This actually strips all params,
        // which is a similar behavior to the JS PolyGerrit redirector code.
        // Stripping just one param is frustratingly difficult without the use
        // of Apache httpclient, which is a dep we don't want here:
        // https://gerrit-review.googlesource.com/#/c/57570/57/gerrit-httpd/BUCK@32
        res.sendRedirect(req.getRequestURL().toString());
      }
      return redirect;
    }

    private boolean isPolyGerritEnabled(HttpServletRequest req) {
      return !options.enableGwtUi() || isPolyGerritCookie(req);
    }

    private boolean isPolyGerritCookie(HttpServletRequest req) {
      UiType type = options.defaultUi();
      Cookie[] all = req.getCookies();
      if (all != null) {
        for (Cookie c : all) {
          if (GERRIT_UI_COOKIE.equals(c.getName())) {
            UiType t = UiType.parse(c.getValue());
            if (t != null) {
              type = t;
              break;
            }
          }
        }
      }
      return type == UiType.POLYGERRIT;
    }

    private void setPolyGerritCookie(HttpServletRequest req, HttpServletResponse res, UiType pref) {
      // Only actually set a cookie if both UIs are enabled in the server;
      // otherwise clear it.
      Cookie cookie = new Cookie(GERRIT_UI_COOKIE, pref.name());
      if (options.enablePolyGerrit() && options.enableGwtUi()) {
        cookie.setPath("/");
        cookie.setSecure(isSecure(req));
        cookie.setMaxAge(GERRIT_UI_COOKIE_MAX_AGE);
      } else {
        cookie.setValue("");
        cookie.setMaxAge(0);
      }
      res.addCookie(cookie);
    }

    private static boolean isSecure(HttpServletRequest req) {
      return req.isSecure() || "https".equals(req.getScheme());
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
