// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.pgm.http.jetty;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.escape.Escaper;
import com.google.common.html.HtmlEscapers;
import com.google.common.io.ByteStreams;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.launcher.GerritLauncher;
import com.google.gerrit.pgm.http.jetty.HttpLog.HttpLogFactory;
import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.config.ThreadSettingsConfig;
import com.google.gwtexpui.linker.server.UserAgentRule;
import com.google.gwtexpui.server.CacheHeaders;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.servlet.GuiceFilter;
import com.google.inject.servlet.GuiceServletContextListener;

import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.BlockingArrayQueue;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.util.RawParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class JettyServer {
  private static final Logger log = LoggerFactory.getLogger(JettyServer.class);

  static class Lifecycle implements LifecycleListener {
    private final JettyServer server;
    private final Config cfg;

    @Inject
    Lifecycle(final JettyServer server, @GerritServerConfig final Config cfg) {
      this.server = server;
      this.cfg = cfg;
    }

    @Override
    public void start() {
      try {
        String origUrl = cfg.getString("httpd", null, "listenUrl");
        boolean rewrite = !Strings.isNullOrEmpty(origUrl)
            && origUrl.endsWith(":0/");
        server.httpd.start();
        if (rewrite) {
          Connector con = server.httpd.getConnectors()[0];
          if (con instanceof ServerConnector) {
            @SuppressWarnings("resource")
            ServerConnector serverCon = (ServerConnector)con;
            String host = serverCon.getHost();
            int port = serverCon.getLocalPort();
            String url = String.format("http://%s:%d", host, port);
            cfg.setString("gerrit", null, "canonicalWebUrl", url);
            cfg.setString("httpd", null, "listenUrl", url);
          }
        }
      } catch (Exception e) {
        throw new IllegalStateException("Cannot start HTTP daemon", e);
      }
    }

    @Override
    public void stop() {
      try {
        server.httpd.stop();
        server.httpd.join();
      } catch (Exception e) {
        throw new IllegalStateException("Cannot stop HTTP daemon", e);
      }
    }
  }

  private final SitePaths site;
  private final Server httpd;

  private boolean reverseProxy;

  /** Location on disk where our WAR file was unpacked to. */
  private Resource baseResource;

  @Inject
  JettyServer(@GerritServerConfig Config cfg,
      ThreadSettingsConfig threadSettingsConfig,
      SitePaths site,
      JettyEnv env,
      HttpLogFactory httpLogFactory)
      throws MalformedURLException, IOException {
    this.site = site;

    httpd = new Server(threadPool(cfg, threadSettingsConfig));
    httpd.setConnectors(listen(httpd, cfg));

    Handler app = makeContext(env, cfg);
    if (cfg.getBoolean("httpd", "requestLog", !reverseProxy)) {
      RequestLogHandler handler = new RequestLogHandler();
      handler.setRequestLog(httpLogFactory.get());
      handler.setHandler(app);
      app = handler;
    }
    if (cfg.getBoolean("httpd", "registerMBeans", false)) {
      MBeanContainer mbean =
          new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
      httpd.addEventListener(mbean);
      httpd.addBean(Log.getRootLogger());
      httpd.addBean(mbean);
    }

    httpd.setHandler(app);
    httpd.setStopAtShutdown(false);
  }

  private Connector[] listen(Server server, Config cfg) {
    // OpenID and certain web-based single-sign-on products can cause
    // some very long headers, especially in the Referer header. We
    // need to use a larger default header size to ensure we have
    // the space required.
    //
    final int requestHeaderSize =
        cfg.getInt("httpd", "requestheadersize", 16386);
    final URI[] listenUrls = listenURLs(cfg);
    final boolean reuseAddress = cfg.getBoolean("httpd", "reuseaddress", true);
    final int acceptors = cfg.getInt("httpd", "acceptorThreads", 2);
    final AuthType authType = cfg.getEnum("auth", null, "type", AuthType.OPENID);

    reverseProxy = isReverseProxied(listenUrls);
    final Connector[] connectors = new Connector[listenUrls.length];
    for (int idx = 0; idx < listenUrls.length; idx++) {
      final URI u = listenUrls[idx];
      final int defaultPort;
      final ServerConnector c;
      HttpConfiguration config = defaultConfig(requestHeaderSize);

      if (AuthType.CLIENT_SSL_CERT_LDAP.equals(authType) && ! "https".equals(u.getScheme())) {
        throw new IllegalArgumentException("Protocol '" + u.getScheme()
            + "' " + " not supported in httpd.listenurl '" + u
            + "' when auth.type = '" + AuthType.CLIENT_SSL_CERT_LDAP.name()
            + "'; only 'https' is supported");
      }

      if ("http".equals(u.getScheme())) {
        defaultPort = 80;
        c = newServerConnector(server, acceptors, config);

      } else if ("https".equals(u.getScheme())) {
        SslContextFactory ssl = new SslContextFactory();
        final Path keystore = getFile(cfg, "sslkeystore", "etc/keystore");
        String password = cfg.getString("httpd", null, "sslkeypassword");
        if (password == null) {
          password = "gerrit";
        }
        ssl.setKeyStorePath(keystore.toAbsolutePath().toString());
        ssl.setTrustStorePath(keystore.toAbsolutePath().toString());
        ssl.setKeyStorePassword(password);
        ssl.setTrustStorePassword(password);

        if (AuthType.CLIENT_SSL_CERT_LDAP.equals(authType)) {
          ssl.setNeedClientAuth(true);

          Path crl = getFile(cfg, "sslcrl", "etc/crl.pem");
          if (Files.exists(crl)) {
            ssl.setCrlPath(crl.toAbsolutePath().toString());
            ssl.setValidatePeerCerts(true);
          }
        }

        defaultPort = 443;

        config.addCustomizer(new SecureRequestCustomizer());
        c = new ServerConnector(server,
            null, null, null, 0, acceptors,
            new SslConnectionFactory(ssl, "http/1.1"),
            new HttpConnectionFactory(config));

      } else if ("proxy-http".equals(u.getScheme())) {
        defaultPort = 8080;
        config.addCustomizer(new ForwardedRequestCustomizer());
        c = newServerConnector(server, acceptors, config);

      } else if ("proxy-https".equals(u.getScheme())) {
        defaultPort = 8080;
        config.addCustomizer(new ForwardedRequestCustomizer());
        config.addCustomizer(new HttpConfiguration.Customizer() {
          @Override
          public void customize(Connector connector,
              HttpConfiguration channelConfig, Request request) {
            request.setScheme(HttpScheme.HTTPS.asString());
            request.setSecure(true);
          }
        });
        c = newServerConnector(server, acceptors, config);

      } else {
        throw new IllegalArgumentException("Protocol '" + u.getScheme() + "' "
            + " not supported in httpd.listenurl '" + u + "';"
            + " only 'http', 'https', 'proxy-http, 'proxy-https'"
            + " are supported");
      }

      try {
        if (u.getHost() == null && (u.getAuthority().equals("*") //
            || u.getAuthority().startsWith("*:"))) {
          // Bind to all local addresses. Port wasn't parsed right by URI
          // due to the illegal host of "*" so replace with a legal name
          // and parse the URI.
          //
          final URI r =
              new URI(u.toString().replace('*', 'A')).parseServerAuthority();
          c.setHost(null);
          c.setPort(0 < r.getPort() ? r.getPort() : defaultPort);
        } else {
          final URI r = u.parseServerAuthority();
          c.setHost(r.getHost());
          c.setPort(0 <= r.getPort() ? r.getPort() : defaultPort);
        }
      } catch (URISyntaxException e) {
        throw new IllegalArgumentException("Invalid httpd.listenurl " + u, e);
      }

      c.setReuseAddress(reuseAddress);
      connectors[idx] = c;
    }
    return connectors;
  }

  private static ServerConnector newServerConnector(Server server,
      int acceptors, HttpConfiguration config) {
    return new ServerConnector(server, null, null, null, 0, acceptors,
        new HttpConnectionFactory(config));
  }

  private HttpConfiguration defaultConfig(int requestHeaderSize) {
    HttpConfiguration config = new HttpConfiguration();
    config.setRequestHeaderSize(requestHeaderSize);
    config.setSendServerVersion(false);
    config.setSendDateHeader(true);
    return config;
  }

  static boolean isReverseProxied(final URI[] listenUrls) {
    for (URI u : listenUrls) {
      if ("http".equals(u.getScheme()) || "https".equals(u.getScheme())) {
        return false;
      }
    }
    return true;
  }

  static URI[] listenURLs(final Config cfg) {
    String[] urls = cfg.getStringList("httpd", null, "listenurl");
    if (urls.length == 0) {
      urls = new String[] {"http://*:8080/"};
    }

    final URI[] r = new URI[urls.length];
    for (int i = 0; i < r.length; i++) {
      final String s = urls[i];
      try {
        r[i] = new URI(s);
      } catch (URISyntaxException e) {
        throw new IllegalArgumentException("Invalid httpd.listenurl " + s, e);
      }
    }
    return r;
  }

  private Path getFile(Config cfg, String name, String def) {
    String path = cfg.getString("httpd", null, name);
    if (path == null || path.length() == 0) {
      path = def;
    }
    return site.resolve(path);
  }

  private ThreadPool threadPool(Config cfg, ThreadSettingsConfig threadSettingsConfig) {
    int maxThreads = threadSettingsConfig.getHttpdMaxThreads();
    int minThreads = cfg.getInt("httpd", null, "minthreads", 5);
    int maxQueued = cfg.getInt("httpd", null, "maxqueued", 200);
    int idleTimeout = (int)MILLISECONDS.convert(60, SECONDS);
    int maxCapacity = maxQueued == 0
        ? Integer.MAX_VALUE
        : Math.max(minThreads, maxQueued);
    QueuedThreadPool pool = new QueuedThreadPool(
        maxThreads,
        minThreads,
        idleTimeout,
        new BlockingArrayQueue<Runnable>(
            minThreads, // capacity,
            minThreads, // growBy,
            maxCapacity // maxCapacity
    ));
    pool.setName("HTTP");
    return pool;
  }

  private Handler makeContext(final JettyEnv env, final Config cfg)
      throws MalformedURLException, IOException {
    final Set<String> paths = new HashSet<>();
    for (URI u : listenURLs(cfg)) {
      String p = u.getPath();
      if (p == null || p.isEmpty()) {
        p = "/";
      }
      while (1 < p.length() && p.endsWith("/")) {
        p = p.substring(0, p.length() - 1);
      }
      paths.add(p);
    }

    final List<ContextHandler> all = new ArrayList<>();
    for (String path : paths) {
      all.add(makeContext(path, env, cfg));
    }

    if (all.size() == 1) {
      // If we only have one context path in our web space, return it
      // without any wrapping so Jetty has less work to do per-request.
      //
      return all.get(0);
    } else {
      // We have more than one path served out of this container so
      // combine them in a handler which supports dispatching to the
      // individual contexts.
      //
      final ContextHandlerCollection r = new ContextHandlerCollection();
      r.setHandlers(all.toArray(new Handler[0]));
      return r;
    }
  }

  private ContextHandler makeContext(final String contextPath,
      final JettyEnv env, final Config cfg) throws MalformedURLException, IOException {
    final ServletContextHandler app = new ServletContextHandler();

    // This enables the use of sessions in Jetty, feature available
    // for Gerrit plug-ins to enable user-level sessions.
    //
    app.setSessionHandler(new SessionHandler());
    app.setErrorHandler(new HiddenErrorHandler());

    // This is the path we are accessed by clients within our domain.
    //
    app.setContextPath(contextPath);

    // Serve static resources directly from our JAR. This way we don't
    // need to unpack them into yet another temporary directory prior to
    // serving to clients.
    //
    app.setBaseResource(getBaseResource(app));

    // HTTP front-end filter to be used as surrogate of Apache HTTP
    // reverse-proxy filtering.
    // It is meant to be used as simpler tiny deployment of custom-made
    // security enforcement (Security tokens, IP-based security filtering, others)
    String filterClassName = cfg.getString("httpd", null, "filterClass");
    if (filterClassName != null) {
      try {
        @SuppressWarnings("unchecked")
        Class<? extends Filter> filterClass =
            (Class<? extends Filter>) Class.forName(filterClassName);
        Filter filter = env.webInjector.getInstance(filterClass);
        app.addFilter(new FilterHolder(filter), "/*",
            EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC));
      } catch (Throwable e) {
        String errorMessage =
            "Unable to instantiate front-end HTTP Filter " + filterClassName;
        log.error(errorMessage, e);
        throw new IllegalArgumentException(errorMessage, e);
      }
    }

    // Perform the same binding as our web.xml would do, but instead
    // of using the listener to create the injector pass the one we
    // already have built.
    //
    GuiceFilter filter = env.webInjector.getInstance(GuiceFilter.class);
    app.addFilter(new FilterHolder(filter), "/*", EnumSet.of(
        DispatcherType.REQUEST,
        DispatcherType.ASYNC));
    app.addEventListener(new GuiceServletContextListener() {
      @Override
      protected Injector getInjector() {
        return env.webInjector;
      }
    });

    // Jetty requires at least one servlet be bound before it will
    // bother running the filter above. Since the filter has all
    // of our URLs except the static resources, the only servlet
    // we need to bind is the default static resource servlet from
    // the Jetty container.
    //
    final ServletHolder ds = app.addServlet(DefaultServlet.class, "/");
    ds.setInitParameter("dirAllowed", "false");
    ds.setInitParameter("redirectWelcome", "false");
    ds.setInitParameter("useFileMappedBuffer", "false");
    ds.setInitParameter("gzip", "true");

    app.setWelcomeFiles(new String[0]);
    return app;
  }

  private Resource getBaseResource(ServletContextHandler app)
      throws IOException {
    if (baseResource == null) {
      try {
        baseResource = unpackWar(GerritLauncher.getDistributionArchive());
      } catch (FileNotFoundException err) {
        if (GerritLauncher.NOT_ARCHIVED.equals(err.getMessage())) {
          baseResource = useDeveloperBuild(app);
        } else {
          throw err;
        }
      }
    }
    return baseResource;
  }

  private static Resource unpackWar(File srcwar) throws IOException {
    File dstwar = makeWarTempDir();
    unpack(srcwar, dstwar);
    return Resource.newResource(dstwar.toURI());
  }

  private static File makeWarTempDir() throws IOException {
    // Obtain our local temporary directory, but it comes back as a file
    // so we have to switch it to be a directory post creation.
    //
    File dstwar = GerritLauncher.createTempFile("gerrit_", "war");
    if (!dstwar.delete() || !dstwar.mkdir()) {
      throw new IOException("Cannot mkdir " + dstwar.getAbsolutePath());
    }

    // Jetty normally refuses to serve out of a symlinked directory, as
    // a security feature. Try to resolve out any symlinks in the path.
    //
    try {
      return dstwar.getCanonicalFile();
    } catch (IOException e) {
      return dstwar.getAbsoluteFile();
    }
  }

  private static void unpack(File srcwar, File dstwar) throws IOException {
    try (ZipFile zf = new ZipFile(srcwar)) {
      final Enumeration<? extends ZipEntry> e = zf.entries();
      while (e.hasMoreElements()) {
        final ZipEntry ze = e.nextElement();
        final String name = ze.getName();

        if (ze.isDirectory()
          || name.startsWith("WEB-INF/")
          || name.startsWith("META-INF/")
          || name.startsWith("com/google/gerrit/launcher/")
          || name.equals("Main.class")) {
          continue;
        }

        final File rawtmp = new File(dstwar, name);
        mkdir(rawtmp.getParentFile());
        rawtmp.deleteOnExit();

        try (FileOutputStream rawout = new FileOutputStream(rawtmp);
            InputStream in = zf.getInputStream(ze)) {
          final byte[] buf = new byte[4096];
          int n;
          while ((n = in.read(buf, 0, buf.length)) > 0) {
            rawout.write(buf, 0, n);
          }
        }
      }
    }
  }

  private static void mkdir(File dir) throws IOException {
    if (!dir.isDirectory()) {
      mkdir(dir.getParentFile());
      if (!dir.mkdir()) {
        throw new IOException("Cannot mkdir " + dir.getAbsolutePath());
      }
      dir.deleteOnExit();
    }
  }

  private Resource useDeveloperBuild(ServletContextHandler app)
      throws IOException {
    final Path dir = GerritLauncher.getDeveloperBuckOut();
    final Path gen = dir.resolve("gen");
    final Path root = dir.getParent();
    final File dstwar = makeWarTempDir();
    File ui = new File(dstwar, "gerrit_ui");
    File p = new File(ui, "permutations");
    mkdir(ui);
    p.createNewFile();
    p.deleteOnExit();

    app.addFilter(new FilterHolder(new Filter() {
      private final boolean gwtuiRecompile =
          System.getProperty("gerrit.disable-gwtui-recompile") == null;
      private final UserAgentRule rule = new UserAgentRule();
      private final Set<String> uaInitialized = new HashSet<>();
      private String lastTarget;
      private long lastTime;

      @Override
      public void doFilter(ServletRequest request, ServletResponse res,
          FilterChain chain) throws IOException, ServletException {
        String pkg = "gerrit-gwtui";
        String target = "ui_" + rule.select((HttpServletRequest) request);
        if (gwtuiRecompile || !uaInitialized.contains(target)) {
          String rule = "//" + pkg + ":" + target;
          // TODO(davido): instead of assuming specific Buck's internal
          // target directory for gwt_binary() artifacts, ask Buck for
          // the location of user agent permutation GWT zip, e. g.:
          // $ buck targets --show_output //gerrit-gwtui:ui_safari \
          //    | awk '{print $2}'
          String child = String.format("%s/__gwt_binary_%s__", pkg, target);
          File zip = gen.resolve(child).resolve(target + ".zip").toFile();

          synchronized (this) {
            try {
              build(root, gen, rule);
            } catch (BuildFailureException e) {
              displayFailure(rule, e.why, (HttpServletResponse) res);
              return;
            }

            if (!target.equals(lastTarget) || lastTime != zip.lastModified()) {
              lastTarget = target;
              lastTime = zip.lastModified();
              unpack(zip, dstwar);
            }
          }
          uaInitialized.add(target);
        }
        chain.doFilter(request, res);
      }

      private void displayFailure(String rule, byte[] why, HttpServletResponse res)
          throws IOException {
        res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        res.setContentType("text/html");
        res.setCharacterEncoding(UTF_8.name());
        CacheHeaders.setNotCacheable(res);

        Escaper html = HtmlEscapers.htmlEscaper();
        try (PrintWriter w = res.getWriter()) {
          w.write("<html><title>BUILD FAILED</title><body>");
          w.format("<h1>%s FAILED</h1>", html.escape(rule));
          w.write("<pre>");
          w.write(html.escape(RawParseUtils.decode(why)));
          w.write("</pre>");
          w.write("</body></html>");
        }
      }

      @Override
      public void init(FilterConfig config) {
      }

      @Override
      public void destroy() {
      }
    }), "/", EnumSet.of(DispatcherType.REQUEST));
    return Resource.newResource(dstwar.toURI());
  }

  private static void build(Path root, Path gen, String target)
      throws IOException, BuildFailureException {
    log.info("buck build " + target);
    Properties properties = loadBuckProperties(gen);
    String buck = MoreObjects.firstNonNull(properties.getProperty("buck"), "buck");
    ProcessBuilder proc = new ProcessBuilder(buck, "build", target)
        .directory(root.toFile())
        .redirectErrorStream(true);
    if (properties.containsKey("PATH")) {
      proc.environment().put("PATH", properties.getProperty("PATH"));
    }
    long start = TimeUtil.nowMs();
    Process rebuild = proc.start();
    byte[] out;
    try (InputStream in = rebuild.getInputStream()) {
      out = ByteStreams.toByteArray(in);
    } finally {
      rebuild.getOutputStream().close();
    }

    int status;
    try {
      status = rebuild.waitFor();
    } catch (InterruptedException e) {
      throw new InterruptedIOException("interrupted waiting for " + buck);
    }
    if (status != 0) {
      throw new BuildFailureException(out);
    }

    long time = TimeUtil.nowMs() - start;
    log.info(String.format("UPDATED    %s in %.3fs", target, time / 1000.0));
  }

  private static Properties loadBuckProperties(Path gen)
      throws FileNotFoundException, IOException {
    Properties properties = new Properties();
    try (InputStream in = new FileInputStream(
        gen.resolve(Paths.get("tools/buck/buck.properties")).toFile())) {
      properties.load(in);
    }
    return properties;
  }

  @SuppressWarnings("serial")
  private static class BuildFailureException extends Exception {
    final byte[] why;

    BuildFailureException(byte[] why) {
      this.why = why;
    }
  }
}
