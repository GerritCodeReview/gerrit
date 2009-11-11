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

import com.google.gerrit.lifecycle.LifecycleListener;
import com.google.gerrit.main.GerritLauncher;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePath;
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
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterMapping;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jgit.lib.Config;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Singleton
public class JettyServer {
  static class Lifecycle implements LifecycleListener {
    private final JettyServer server;
    @Inject
    Lifecycle(final JettyServer server) {
      this.server=server;
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

  private final File sitePath;
  private final Server httpd;

  @Inject
  JettyServer(@GerritServerConfig final Config cfg,
      @SitePath final File sitePath, final JettyEnv env)
      throws MalformedURLException, IOException {
    this.sitePath = sitePath;

    Handler app = makeContext(env, cfg);

    httpd = new Server();
    httpd.setConnectors(listen(cfg));
    httpd.setThreadPool(threadPool(cfg));
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

    final Connector[] connectors = new Connector[listenUrls.length];
    for (int idx = 0; idx < listenUrls.length; idx++) {
      final URI u = listenUrls[idx];
      final int defaultPort;
      final SelectChannelConnector c;

      if ("http".equals(u.getScheme())) {
        defaultPort = 80;
        c = new SelectChannelConnector();

      } else if ("https".equals(u.getScheme())) {
        final SslSelectChannelConnector ssl = new SslSelectChannelConnector();
        final File keystore = getFile(cfg, "sslkeystore", "keystore");
        String password = cfg.getString("httpd", null, "sslkeypassword");
        if (password == null) {
          password = "gerrit";
        }
        ssl.setKeystore(keystore.getAbsolutePath());
        ssl.setTruststore(keystore.getAbsolutePath());
        ssl.setKeyPassword(password);
        ssl.setTrustPassword(password);

        defaultPort = 443;
        c = ssl;

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

  private URI[] listenURLs(final Config cfg) {
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

    File loc = new File(path);
    if (!loc.isAbsolute()) {
      loc = new File(sitePath, path);
    }
    return loc;
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
      paths.add(u.getPath() != null ? u.getPath() : "/");
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

    // This is the path we are accessed by clients within our domain.
    //
    app.setContextPath(contextPath);

    // Serve static resources directly from our JAR. This way we don't
    // need to unpack them into yet another temporary directory prior to
    // serving to clients.
    //
    final File war = GerritLauncher.getDistributionArchive();
    app.setBaseResource(Resource.newResource("jar:" + war.toURI() + "!/"));

    // Perform the same binding as our web.xml would do, but instead
    // of using the listener to create the injector pass the one we
    // already have built.
    //
    app.addFilter(GuiceFilter.class, "/*", FilterMapping.DEFAULT);
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
    app.addServlet(DefaultServlet.class, "/");

    return app;
  }
}
