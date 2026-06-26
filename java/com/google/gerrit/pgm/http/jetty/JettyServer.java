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

import static com.google.gerrit.httpd.CacheBasedWebSession.MAX_AGE_MINUTES;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.gerrit.extensions.client.AuthType;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.httpd.RemoteUserUtil;
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
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import org.eclipse.jetty.ee8.nested.SessionHandler;
import org.eclipse.jetty.ee8.servlet.DefaultServlet;
import org.eclipse.jetty.ee8.servlet.FilterHolder;
import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.UriCompliance;
import org.eclipse.jetty.io.ConnectionStatistics;
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
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.util.BlockingArrayQueue;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jgit.lib.Config;

@Singleton
public class JettyServer {

  private static final ForwardedRequestCustomizer FORWARDED_REQUEST_CUSTOMIZER =
      new ForwardedRequestCustomizer() {
        @Override
        public Request customize(Request request, HttpFields.Mutable responseHeaders) {
          /*
           * The default behavior of ForwardedRequestCustomizer is to overwrite the remote address
           * with the value of the X-Forwarded-For header, if present.
           * However, it does not "remember" the original remote address and therefore would
           * prevent any validation against it.
           *
           * ForwardedRequestCustomizer's original code fragment:
           * <code>
           * if (forwarded.hasFor())
           * {
           *     int forPort = forwarded._for._port > 0 ? forwarded._for._port : request.getRemotePort();
           *     request.setRemoteAddr(InetSocketAddress.createUnresolved(forwarded._for._host, forPort));
           * }
           * </code>
           *
           * What we want to achieve here is to remember what it was the original proxy address before
           * calling super.customize() and give the possibility to fetch it later down the chain.
           */
          request.setAttribute(
              RemoteUserUtil.PROXY_REMOTE_ADDRESS_ATTR,
              ((InetSocketAddress) request.getConnectionMetaData().getRemoteSocketAddress())
                  .getAddress()
                  .getHostAddress());
          return super.customize(request, responseHeaders);
        }
      };

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
  private final SessionHandler sessionHandler;
  private final AtomicLong sessionsCounter;

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
    sessionHandler = new SessionHandler();
    sessionsCounter = new AtomicLong();

    /* Code used for testing purposes for making assertions
     * on the number of active HTTP sessions.
     */
    sessionHandler.addEventListener(
        new HttpSessionListener() {

          @Override
          public void sessionDestroyed(HttpSessionEvent se) {
            sessionsCounter.decrementAndGet();
          }

          @Override
          public void sessionCreated(HttpSessionEvent se) {
            sessionsCounter.incrementAndGet();
          }
        });

    sessionHandler.setMaxInactiveInterval(
        (int)
            cfg.getTimeUnit(
                "cache",
                "web_sessions",
                "maxAge",
                SECONDS.convert(MAX_AGE_MINUTES, MINUTES),
                SECONDS));

    Handler app = makeContext(env, cfg, sessionHandler);
    if (cfg.getBoolean("httpd", "requestLog", !reverseProxy)) {
      httpd.setRequestLog(httpLogFactory.get());
    }
    if (cfg.getBoolean("httpd", "registerMBeans", false)) {
      MBeanContainer mbean = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
      httpd.addEventListener(mbean);
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

  @VisibleForTesting
  public long numActiveSessions() {
    return sessionsCounter.longValue();
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

      // Jetty 12 changed the default UriCompliance to RFC3986 (strict),
      // which rejects two URI shapes Gerrit's REST API depends on:
      //   - AMBIGUOUS_PATH_SEPARATOR: encoded '/' (%2F) inside a path
      //     segment, used for project/branch names (e.g.
      //     DELETE /projects/foo%2Fbar/branches/refs%2Fheads%2Ftest);
      //   - AMBIGUOUS_PATH_ENCODING: an encoded character that itself
      //     decodes to a reserved one (e.g. %25 decoding to '%'),
      //     hit by /changes/%3C%25%3DFOO%25%3E~1/detail where the
      //     decoded identifier '<%=FOO%>' contains a literal '%'.
      // Allow exactly these two violations; broader presets like LEGACY
      // also permit suspicious characters, USER_INFO, FRAGMENT etc. that
      // Gerrit's REST surface does not need.
      config.setUriCompliance(
          UriCompliance.from(
              EnumSet.of(
                  UriCompliance.Violation.AMBIGUOUS_PATH_SEPARATOR,
                  UriCompliance.Violation.AMBIGUOUS_PATH_ENCODING)));

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
        config.addCustomizer(FORWARDED_REQUEST_CUSTOMIZER);
        c = newServerConnector(server, acceptors, config);

      } else if ("proxy-https".equals(u.getScheme())) {
        defaultPort = 8080;
        config.addCustomizer(FORWARDED_REQUEST_CUSTOMIZER);
        // For a proxy that terminates TLS, mark every request as HTTPS
        // unconditionally. ForwardedRequestCustomizer alone only sets
        // isSecure() when the proxy sends X-Forwarded-Proto=https or
        // X-Proxied-Https=on; this wrapper covers proxies that don't.
        // Jetty 12's HttpConfiguration.Customizer returns a (possibly
        // wrapped) Request, so wrap the URI's scheme and override isSecure().
        config.addCustomizer(
            (request, responseHeaders) ->
                new Request.Wrapper(request) {
                  @Override
                  public HttpURI getHttpURI() {
                    return HttpURI.build(super.getHttpURI())
                        .scheme(HttpScheme.HTTPS.asString())
                        .asImmutable();
                  }

                  @Override
                  public boolean isSecure() {
                    return true;
                  }
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
    // Jetty 12 changed the default for relativeRedirectAllowed from false to
    // true (https://github.com/jetty/jetty.project/issues/11947); restore the
    // pre-Jetty-12 behaviour Gerrit's redirect handling expects.
    config.setRelativeRedirectAllowed(false);
    config.setSendServerVersion(false);
    config.setSendDateHeader(true);
    // TODO(davido): consider configuring HttpConfiguration.setMinResponseDataRate
    // and setMinRequestDataRate. Jetty 12 removed setBlockingTimeout (deprecated
    // in Jetty 9) in favour of these minimum bytes/sec knobs. Gerrit's previous
    // setBlockingTimeout(0) was an explicit opt-in to Jetty 9's special
    // "0 == use idle timeout" semantic (the Jetty 9 default was -1, i.e.
    // blocking-timeout disabled). In Jetty 12 the blocking-timeout knob no
    // longer exists; the connector's idle timeout governs all stalled IO.
    // Revisit if slow-client mitigation becomes desirable.

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

  // BlockingArrayQueue's 3-arg constructor is deprecated for removal in
  // Jetty 12.1.2+ -- the public API no longer offers a bounded-but-growable
  // queue matching Gerrit's historical (initial = minThreads, grow up to
  // maxCapacity) semantics. Revisit when Jetty 12.2 drops it: either switch
  // to the unbounded constructor (Jetty's own recommendation) or to the
  // fixed-size 1-arg one.
  @SuppressWarnings("removal")
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

  private Handler makeContext(JettyEnv env, Config cfg, SessionHandler sessionHandler) {
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

    final List<Handler> all = new ArrayList<>();
    for (String path : paths) {
      all.add(makeContext(path, env, cfg, sessionHandler));
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

  private Handler makeContext(
      final String contextPath, JettyEnv env, Config cfg, SessionHandler sessionHandler) {
    final ServletContextHandler app = new ServletContextHandler();

    // This enables the use of sessions in Jetty, feature available
    // for Gerrit plug-ins to enable user-level sessions.
    //
    app.setSessionHandler(sessionHandler);
    app.setErrorHandler(new HiddenErrorHandler());

    // This is the path we are accessed by clients within our domain.
    //
    app.setContextPath(contextPath);

    // HTTP front-end filters to be used as surrogate of Apache HTTP
    // reverse-proxy filtering.
    // It is meant to be used as simpler tiny deployment of custom-made
    // security enforcement (Security tokens, IP-based security filtering, others)
    String[] filterClassNames = cfg.getStringList("httpd", null, "filterClass");
    String sameSiteAttribute = cfg.getString("httpd", null, "sameSite");
    if (sameSiteAttribute != null) {
      filterClassNames =
          Stream.concat(Arrays.stream(filterClassNames), Stream.of(SameSiteFilter.class.getName()))
              .toArray(String[]::new);
    }
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
      } catch (Exception e) {
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
    // ee8 ContextHandler implements Supplier<org.eclipse.jetty.server.Handler>;
    // unwrap to the core Handler for installation into the server's handler tree.
    return app.get();
  }
}
