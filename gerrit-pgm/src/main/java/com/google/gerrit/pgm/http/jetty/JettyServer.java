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

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.launcher.GerritLauncher;
import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.servlet.GuiceFilter;
import com.google.inject.servlet.GuiceServletContextListener;

import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jgit.lib.Config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.servlet.DispatcherType;

@Singleton
public class JettyServer {
  static class Lifecycle implements LifecycleListener {
    private final JettyServer server;

    @Inject
    Lifecycle(final JettyServer server) {
      this.server = server;
    }

    @Override
    public void start() {
      try {
        server.httpd.start();
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
  JettyServer(@GerritServerConfig final Config cfg, final SitePaths site,
      final JettyEnv env)
      throws MalformedURLException, IOException {
    this.site = site;

    httpd = new Server();
    httpd.setConnectors(listen(cfg));
    httpd.setThreadPool(threadPool(cfg));

    Handler app = makeContext(env, cfg);
    if (cfg.getBoolean("httpd", "requestlog", !reverseProxy)) {
      RequestLogHandler handler = new RequestLogHandler();
      handler.setRequestLog(new HttpLog(site, cfg));
      handler.setHandler(app);
      app = handler;
    }
    httpd.setHandler(app);

    httpd.setStopAtShutdown(false);
    httpd.setSendDateHeader(true);
    httpd.setSendServerVersion(false);
    httpd.setGracefulShutdown((int) MILLISECONDS.convert(1, SECONDS));
  }

  private Connector[] listen(final Config cfg) {
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
    final AuthType authType = ConfigUtil.getEnum(cfg, "auth", null, "type", AuthType.OPENID);

    reverseProxy = isReverseProxied(listenUrls);
    final Connector[] connectors = new Connector[listenUrls.length];
    for (int idx = 0; idx < listenUrls.length; idx++) {
      final URI u = listenUrls[idx];
      final int defaultPort;
      final SelectChannelConnector c;

      if (AuthType.CLIENT_SSL_CERT_LDAP.equals(authType) && ! "https".equals(u.getScheme())) {
        throw new IllegalArgumentException("Protocol '" + u.getScheme()
            + "' " + " not supported in httpd.listenurl '" + u
            + "' when auth.type = '" + AuthType.CLIENT_SSL_CERT_LDAP.name()
            + "'; only 'https' is supported");
      }

      if ("http".equals(u.getScheme())) {
        defaultPort = 80;
        c = new SelectChannelConnector();
      } else if ("https".equals(u.getScheme())) {
        SslContextFactory ssl = new SslContextFactory();
        final File keystore = getFile(cfg, "sslkeystore", "etc/keystore");
        String password = cfg.getString("httpd", null, "sslkeypassword");
        if (password == null) {
          password = "gerrit";
        }
        ssl.setKeyStorePath(keystore.getAbsolutePath());
        ssl.setTrustStore(keystore.getAbsolutePath());
        ssl.setKeyStorePassword(password);
        ssl.setTrustStorePassword(password);

        if (AuthType.CLIENT_SSL_CERT_LDAP.equals(authType)) {
          ssl.setNeedClientAuth(true);
        }

        defaultPort = 443;
        c = new SslSelectChannelConnector(ssl);

      } else if ("proxy-http".equals(u.getScheme())) {
        defaultPort = 8080;
        c = new SelectChannelConnector();
        c.setForwarded(true);

      } else if ("proxy-https".equals(u.getScheme())) {
        defaultPort = 8080;
        c = new SelectChannelConnector() {
          @Override
          public void customize(EndPoint endpoint, Request request)
              throws IOException {
            request.setScheme("https");
            super.customize(endpoint, request);
          }
        };
        c.setForwarded(true);

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
          c.setPort(0 < r.getPort() ? r.getPort() : defaultPort);
        }
      } catch (URISyntaxException e) {
        throw new IllegalArgumentException("Invalid httpd.listenurl " + u, e);
      }

      c.setRequestHeaderSize(requestHeaderSize);
      c.setAcceptors(acceptors);
      c.setReuseAddress(reuseAddress);
      c.setStatsOn(false);

      connectors[idx] = c;
    }
    return connectors;
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

  private File getFile(final Config cfg, final String name, final String def) {
    String path = cfg.getString("httpd", null, name);
    if (path == null || path.length() == 0) {
      path = def;
    }
    return site.resolve(path);
  }

  private ThreadPool threadPool(Config cfg) {
    final QueuedThreadPool pool = new QueuedThreadPool();
    pool.setName("HTTP");
    pool.setMinThreads(cfg.getInt("httpd", null, "minthreads", 5));
    pool.setMaxThreads(cfg.getInt("httpd", null, "maxthreads", 25));
    pool.setMaxQueued(cfg.getInt("httpd", null, "maxqueued", 50));
    return pool;
  }

  private Handler makeContext(final JettyEnv env, final Config cfg)
      throws MalformedURLException, IOException {
    final Set<String> paths = new HashSet<String>();
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

    final List<ContextHandler> all = new ArrayList<ContextHandler>();
    for (String path : paths) {
      all.add(makeContext(path, env));
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
      final JettyEnv env) throws MalformedURLException, IOException {
    final ServletContextHandler app = new ServletContextHandler();

    // This enables the use of sessions in Jetty, feature available
    // for Gerrit plug-ins to enable user-level sessions.
    //
    app.setSessionHandler(new SessionHandler());

    // This is the path we are accessed by clients within our domain.
    //
    app.setContextPath(contextPath);

    // Serve static resources directly from our JAR. This way we don't
    // need to unpack them into yet another temporary directory prior to
    // serving to clients.
    //
    app.setBaseResource(getBaseResource());

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

  private Resource getBaseResource() throws IOException {
    if (baseResource == null) {
      try {
        baseResource = unpackWar(GerritLauncher.getDistributionArchive());
      } catch (FileNotFoundException err) {
        if (err.getMessage() == GerritLauncher.NOT_ARCHIVED) {
          baseResource = useDeveloperBuild();
        } else {
          throw err;
        }
      }
    }
    return baseResource;
  }

  private Resource unpackWar(File srcwar) throws IOException {
    // Obtain our local temporary directory, but it comes back as a file
    // so we have to switch it to be a directory post creation.
    //
    File dstwar = File.createTempFile("gerrit_", "_war", site.tmp_dir);;
    if (!dstwar.delete() || !dstwar.mkdir()) {
      throw new IOException("Cannot mkdir " + dstwar.getAbsolutePath());
    }

    // Jetty normally refuses to serve out of a symlinked directory, as
    // a security feature. Try to resolve out any symlinks in the path.
    //
    try {
      dstwar = dstwar.getCanonicalFile();
    } catch (IOException e) {
      dstwar = dstwar.getAbsoluteFile();
    }

    final ZipFile zf = new ZipFile(srcwar);
    try {
      final Enumeration<? extends ZipEntry> e = zf.entries();
      while (e.hasMoreElements()) {
        final ZipEntry ze = e.nextElement();
        final String name = ze.getName();

        if (ze.isDirectory()) continue;
        if (name.startsWith("WEB-INF/")) continue;
        if (name.startsWith("META-INF/")) continue;
        if (name.startsWith("com/google/gerrit/launcher/")) continue;
        if (name.equals("Main.class")) continue;

        final File rawtmp = new File(dstwar, name);
        mkdir(rawtmp.getParentFile());
        rawtmp.deleteOnExit();

        final FileOutputStream rawout = new FileOutputStream(rawtmp);
        try {
          final InputStream in = zf.getInputStream(ze);
          try {
            final byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf, 0, buf.length)) > 0) {
              rawout.write(buf, 0, n);
            }
          } finally {
            in.close();
          }
        } finally {
          rawout.close();
        }
      }
    } finally {
      zf.close();
    }

    return Resource.newResource(dstwar.toURI());
  }

  private void mkdir(final File dir) throws IOException {
    if (!dir.isDirectory()) {
      mkdir(dir.getParentFile());
      if (!dir.mkdir())
        throw new IOException("Cannot mkdir " + dir.getAbsolutePath());
      dir.deleteOnExit();
    }
  }

  private Resource useDeveloperBuild() throws IOException {
    // Find ourselves in the CLASSPATH. We should be a loose class file.
    //
    URL u = getClass().getResource(getClass().getSimpleName() + ".class");
    if (u == null) {
      throw new FileNotFoundException("Cannot find web application root");
    }
    if (!"file".equals(u.getProtocol())) {
      throw new FileNotFoundException("Cannot find web root from " + u);
    }

    // Pop up to the top level classes folder that contains us.
    //
    File dir = new File(u.getPath());
    String myName = getClass().getName();
    for (;;) {
      int dot = myName.lastIndexOf('.');
      if (dot < 0) {
        dir = dir.getParentFile();
        break;
      }
      myName = myName.substring(0, dot);
      dir = dir.getParentFile();
    }

    if (!dir.getName().equals("classes")) {
      throw new FileNotFoundException("Cannot find web root from " + u);
    }
    dir = dir.getParentFile(); // pop classes

    if ("buck-out".equals(dir.getName())) {
      // TODO(sop) Remove hacky Eclipse Buck support.
      return unpackWar(new File(dir, "gen/gerrit-gwtui/ui_dbg.zip"));
    } else if ("target".equals(dir.getName())) {
      return useMavenDeveloperBuild(dir);
    } else {
      throw new FileNotFoundException("Cannot find web root from " + u);
    }
  }

  private Resource useMavenDeveloperBuild(File dir) throws IOException {
    dir = dir.getParentFile(); // pop target
    dir = dir.getParentFile(); // pop the module we are in

    // Drop down into gerrit-gwtui to find the WAR assets we need.
    //
    dir = new File(new File(dir, "gerrit-gwtui"), "target");
    final File[] entries = dir.listFiles();
    if (entries == null) {
      throw new FileNotFoundException("No " + dir);
    }
    for (File e : entries) {
      if (e.isDirectory() /* must be a directory */
          && e.getName().startsWith("gerrit-gwtui-")
          && new File(e, "gerrit_ui/gerrit_ui.nocache.js").isFile()) {
        return Resource.newResource(e.toURI());
      }
    }
    throw new FileNotFoundException("No " + dir + "/gerrit-gwtui-*");
  }
}
