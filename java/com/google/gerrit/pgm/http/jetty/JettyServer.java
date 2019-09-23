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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.client.AuthType;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.pgm.http.jetty.HttpLog.HttpLogFactory;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.config.ThreadSettingsConfig;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.servlet.GuiceFilter;
import com.google.inject.servlet.GuiceServletContextListener;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.io.ConnectionStatistics;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.BlockingArrayQueue;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jgit.lib.Config;

@Singleton
public class JettyServer {
  static class Lifecycle implements LifecycleListener {
    private final JettyServer server;
    private final Config cfg;

    @Inject
    Lifecycle(JettyServer server, @GerritServerConfig Config cfg) {
      this.server = server;
      this.cfg = cfg;
    }

    @Override
    public void start() {
      try {
        String origUrl = cfg.getString("httpd", null, "listenUrl");
        boolean rewrite = !Strings.isNullOrEmpty(origUrl) && origUrl.endsWith(":0/");
        server.httpd.start();
        if (rewrite) {
          Connector con = server.httpd.getConnectors()[0];
          if (con instanceof ServerConnector) {
            @SuppressWarnings("resource")
            ServerConnector serverCon = (ServerConnector) con;
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

  static class Metrics {
    private final QueuedThreadPool threadPool;
    private ConnectionStatistics connStats;

    Metrics(QueuedThreadPool threadPool, ConnectionStatistics connStats) {
      this.threadPool = threadPool;
      this.connStats = connStats;
    }

    public int getIdleThreads() {
      return threadPool.getIdleThreads();
    }

    public int getBusyThreads() {
      return threadPool.getBusyThreads();
    }

    public int getReservedThreads() {
      return threadPool.getReservedThreads();
    }

    public int getMinThreads() {
      return threadPool.getMinThreads();
    }

    public int getMaxThreads() {
      return threadPool.getMaxThreads();
    }

    public int getThreads() {
      return threadPool.getThreads();
    }

    public int getQueueSize() {
      return threadPool.getQueueSize();
    }

    public boolean isLowOnThreads() {
      return threadPool.isLowOnThreads();
    }

    public long getConnections() {
      return connStats.getConnections();
    }

    public long getConnectionsTotal() {
      return connStats.getConnectionsTotal();
    }

    public long getConnectionDurationMax() {
      return connStats.getConnectionDurationMax();
    }

    public double getConnectionDurationMean() {
      return connStats.getConnectionDurationMean();
    }

    public double getConnectionDurationStdDev() {
      return connStats.getConnectionDurationStdDev();
    }

    public long getReceivedMessages() {
      return connStats.getReceivedMessages();
    }

    public long getSentMessages() {
      return connStats.getSentMessages();
    }

    public long getReceivedBytes() {
      return connStats.getReceivedBytes();
    }

    public long getSentBytes() {
      return connStats.getSentBytes();
    }
  }

  private final SitePaths site;
  private final Server httpd;
  private final Metrics metrics;
  private boolean reverseProxy;
  private ConnectionStatistics connStats;

  @Inject
  JettyServer(
      @GerritServerConfig Config cfg,
      ThreadSettingsConfig threadSettingsConfig,
      SitePaths site,
      JettyEnv env,
      HttpLogFactory httpLogFactory) {
    this.site = site;

    QueuedThreadPool pool = threadPool(cfg, threadSettingsConfig);
    httpd = new Server(pool);
    httpd.setConnectors(listen(httpd, cfg));
    connStats = new ConnectionStatistics();
    for (Connector connector : httpd.getConnectors()) {
      connector.addBean(connStats);
    }
    metrics = new Metrics(pool, connStats);

    Handler app = makeContext(env, cfg);
    if (cfg.getBoolean("httpd", "requestLog", !reverseProxy)) {
      RequestLogHandler handler = new RequestLogHandler();
      handler.setRequestLog(httpLogFactory.get());
      handler.setHandler(app);
      app = handler;
    }
    if (cfg.getBoolean("httpd", "registerMBeans", false)) {
      MBeanContainer mbean = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
      httpd.addEventListener(mbean);
      httpd.addBean(Log.getRootLogger());
      httpd.addBean(mbean);
    }

    long gracefulStopTimeout =
        cfg.getTimeUnit("httpd", null, "gracefulStopTimeout", 0L, TimeUnit.MILLISECONDS);
    if (gracefulStopTimeout > 0) {
      StatisticsHandler statsHandler = new StatisticsHandler();
      statsHandler.setHandler(app);
      app = statsHandler;
      httpd.setStopTimeout(gracefulStopTimeout);
    }

    httpd.setHandler(app);
    httpd.setStopAtShutdown(false);
  }

  Metrics getMetrics() {
    return metrics;
  }

  private Connector[] listen(Server server, Config cfg) {
    // OpenID and certain web-based single-sign-on products can cause
    // some very long headers, especially in the Referer header. We
    // need to use a larger default header size to ensure we have
    // the space required.
    //
    final int requestHeaderSize = cfg.getInt("httpd", "requestheadersize", 16386);
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

      if (AuthType.CLIENT_SSL_CERT_LDAP.equals(authType) && !"https".equals(u.getScheme())) {
        throw new IllegalArgumentException(
            "Protocol '"
                + u.getScheme()
                + "' "
                + " not supported in httpd.listenurl '"
                + u
                + "' when auth.type = '"
                + AuthType.CLIENT_SSL_CERT_LDAP.name()
                + "'; only 'https' is supported");
      }

      if ("http".equals(u.getScheme())) {
        defaultPort = 80;
        c = newServerConnector(server, acceptors, config);

      } else if ("https".equals(u.getScheme())) {
        SslContextFactory.Server ssl = new SslContextFactory.Server();
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

          Path crl = getFile(cfg, "sslCrl", "etc/crl.pem");
          if (Files.exists(crl)) {
            ssl.setCrlPath(crl.toAbsolutePath().toString());
            ssl.setValidatePeerCerts(true);
          }
        }

        defaultPort = 443;

        config.addCustomizer(new SecureRequestCustomizer());
        c =
            new ServerConnector(
                server,
                null,
                null,
                null,
                0,
                acceptors,
                new SslConnectionFactory(ssl, "http/1.1"),
                new HttpConnectionFactory(config));

      } else if ("proxy-http".equals(u.getScheme())) {
        defaultPort = 8080;
        config.addCustomizer(new ForwardedRequestCustomizer());
        c = newServerConnector(server, acceptors, config);

      } else if ("proxy-https".equals(u.getScheme())) {
        defaultPort = 8080;
        config.addCustomizer(new ForwardedRequestCustomizer());
        config.addCustomizer(
            (connector, channelConfig, request) -> {
              request.setHttpURI(HttpURI.build(request.getHttpURI()).scheme(HttpScheme.HTTPS));
              request.setSecure(true);
            });
        c = newServerConnector(server, acceptors, config);

      } else {
        throw new IllegalArgumentException(
            "Protocol '"
                + u.getScheme()
                + "' "
                + " not supported in httpd.listenurl '"
                + u
                + "';"
                + " only 'http', 'https', 'proxy-http, 'proxy-https'"
                + " are supported");
      }

      try {
        if (u.getHost() == null
            && (u.getAuthority().equals("*") //
                || u.getAuthority().startsWith("*:"))) {
          // Bind to all local addresses. Port wasn't parsed right by URI
          // due to the illegal host of "*" so replace with a legal name
          // and parse the URI.
          //
          final URI r = new URI(u.toString().replace('*', 'A')).parseServerAuthority();
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
      c.setInheritChannel(cfg.getBoolean("httpd", "inheritChannel", false));
      c.setReuseAddress(reuseAddress);
      c.setIdleTimeout(cfg.getTimeUnit("httpd", null, "idleTimeout", 30000L, MILLISECONDS));
      connectors[idx] = c;
    }
    return connectors;
  }

  private static ServerConnector newServerConnector(
      Server server, int acceptors, HttpConfiguration config) {
    return new ServerConnector(
        server, null, null, null, 0, acceptors, new HttpConnectionFactory(config));
  }

  private HttpConfiguration defaultConfig(int requestHeaderSize) {
    HttpConfiguration config = new HttpConfiguration();
    config.setRequestHeaderSize(requestHeaderSize);
    config.setSendServerVersion(false);
    config.setSendDateHeader(true);
    return config;
  }

  static boolean isReverseProxied(URI[] listenUrls) {
    for (URI u : listenUrls) {
      if ("http".equals(u.getScheme()) || "https".equals(u.getScheme())) {
        return false;
      }
    }
    return true;
  }

  static URI[] listenURLs(Config cfg) {
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

  private QueuedThreadPool threadPool(Config cfg, ThreadSettingsConfig threadSettingsConfig) {
    int maxThreads = threadSettingsConfig.getHttpdMaxThreads();
    int minThreads = cfg.getInt("httpd", null, "minthreads", 5);
    int maxQueued = cfg.getInt("httpd", null, "maxqueued", 200);
    int idleTimeout = (int) MILLISECONDS.convert(60, SECONDS);
    int maxCapacity = maxQueued == 0 ? Integer.MAX_VALUE : Math.max(minThreads, maxQueued);
    QueuedThreadPool pool =
        new QueuedThreadPool(
            maxThreads,
            minThreads,
            idleTimeout,
            new BlockingArrayQueue<>(
                minThreads, // capacity,
                minThreads, // growBy,
                maxCapacity // maxCapacity
                ));
    pool.setName("HTTP");
    return pool;
  }

  private Handler makeContext(JettyEnv env, Config cfg) {
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
    }
    // We have more than one path served out of this container so
    // combine them in a handler which supports dispatching to the
    // individual contexts.
    //
    final ContextHandlerCollection r = new ContextHandlerCollection();
    r.setHandlers(all.toArray(new Handler[0]));
    return r;
  }

  private ContextHandler makeContext(final String contextPath, JettyEnv env, Config cfg) {
    final ServletContextHandler app = new ServletContextHandler();

    // This enables the use of sessions in Jetty, feature available
    // for Gerrit plug-ins to enable user-level sessions.
    //
    app.setSessionHandler(new SessionHandler());
    app.setErrorHandler(new HiddenErrorHandler());

    // This is the path we are accessed by clients within our domain.
    //
    app.setContextPath(contextPath);

    // HTTP front-end filters to be used as surrogate of Apache HTTP
    // reverse-proxy filtering.
    // It is meant to be used as simpler tiny deployment of custom-made
    // security enforcement (Security tokens, IP-based security filtering, others)
    String[] filterClassNames = cfg.getStringList("httpd", null, "filterClass");
    for (String filterClassName : filterClassNames) {
      try {
        @SuppressWarnings("unchecked")
        Class<? extends Filter> filterClass =
            (Class<? extends Filter>) Class.forName(filterClassName);
        Filter filter = env.webInjector.getInstance(filterClass);

        Map<String, String> initParams = new HashMap<>();
        Set<String> initParamKeys = cfg.getNames("filterClass", filterClassName, true);
        initParamKeys.forEach(
            paramKey -> {
              String paramValue = cfg.getString("filterClass", filterClassName, paramKey);
              initParams.put(paramKey, paramValue);
            });

        FilterHolder filterHolder = new FilterHolder(filter);
        if (initParams.size() > 0) {
          filterHolder.setInitParameters(initParams);
        }
        app.addFilter(filterHolder, "/*", EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC));
      } catch (Throwable e) {
        throw new IllegalArgumentException(
            "Unable to instantiate front-end HTTP Filter " + filterClassName, e);
      }
    }

    // Perform the same binding as our web.xml would do, but instead
    // of using the listener to create the injector pass the one we
    // already have built.
    //
    GuiceFilter filter = env.webInjector.getInstance(GuiceFilter.class);
    app.addFilter(
        new FilterHolder(filter), "/*", EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC));
    app.addEventListener(
        new GuiceServletContextListener() {
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
}
