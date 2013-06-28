// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.pgm.Daemon;
import com.google.gerrit.pgm.Init;
import com.google.gerrit.pgm.Reindex;
import com.google.gerrit.server.config.FactoryModule;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.util.SocketUtil;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class GerritServer {

  static GerritServer startWithIndex(Config base) throws Exception {
    return start(base, false, true);
  }

  /** Returns fully started Gerrit server */
  static GerritServer start(Config base, boolean memory) throws Exception {
    return start(base, memory, false);
  }

  private static GerritServer start(Config base, boolean memory, boolean index)
      throws Exception {
    if (memory && index) {
      throw new IllegalStateException("cannot create index in memory");
    }
    final CyclicBarrier serverStarted = new CyclicBarrier(2);
    final Daemon daemon = new Daemon(new Runnable() {
      public void run() {
        try {
          serverStarted.await();
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        } catch (BrokenBarrierException e) {
          throw new RuntimeException(e);
        }
      }
    });

    final File site;
    ExecutorService daemonService = null;
    if (memory) {
      site = null;
      Config cfg = base != null ? base : new Config();
      mergeTestConfig(cfg);
      cfg.setBoolean("httpd", null, "requestLog", false);
      cfg.setBoolean("sshd", null, "requestLog", false);
      daemon.setDatabaseForTesting(ImmutableList.<Module>of(
          new InMemoryTestingDatabaseModule(cfg)));
      daemon.start();
    } else {
      Config cfg = base != null ? base : new Config();
      if (index) {
        cfg.setString("index", null, "type", "lucene");
      }
      site = initSite(cfg);
      if (index) {
        reindex(site);
      }
      daemonService = Executors.newSingleThreadExecutor();
      daemonService.submit(new Callable<Void>() {
        public Void call() throws Exception {
          int rc = daemon.main(new String[] {"-d", site.getPath(), "--headless" });
          if (rc != 0) {
            System.out.println("Failed to start Gerrit daemon. Check "
                + site.getPath() + "/logs/error_log");
            serverStarted.reset();
          }
          return null;
        };
      });
      serverStarted.await();
      System.out.println("Gerrit Server Started");
    }

    Injector i = createTestInjector(daemon);
    return new GerritServer(site, i, daemon, daemonService);
  }

  private static File initSite(Config base) throws Exception {
    File tmp = TempFileUtil.createTempDirectory();
    Init init = new Init();
    int rc = init.main(new String[] {
        "-d", tmp.getPath(), "--batch", "--no-auto-start",
        "--skip-plugins"});
    if (rc != 0) {
      throw new RuntimeException("Couldn't initialize site");
    }

    MergeableFileBasedConfig cfg = new MergeableFileBasedConfig(
        new File(new File(tmp, "etc"), "gerrit.config"),
        FS.DETECTED);
    cfg.load();
    cfg.merge(base);
    mergeTestConfig(cfg);
    cfg.save();
    return tmp;
  }

  /** Runs the reindex command. Works only if the site is not currently running. */
  private static void reindex(File site) throws Exception {
    Reindex reindex = new Reindex();
    int rc = reindex.main(new String[] {"-d", site.getPath()});
    if (rc != 0) {
      throw new RuntimeException("Reindex failed");
    }
  }

  private static void mergeTestConfig(Config cfg)
      throws IOException {
    InetSocketAddress http = newPort();
    InetSocketAddress sshd = newPort();
    String url = "http://" + format(http) + "/";

    cfg.setString("gerrit", null, "canonicalWebUrl", url);
    cfg.setString("httpd", null, "listenUrl", url);
    cfg.setString("sshd", null, "listenAddress", format(sshd));
    cfg.setString("cache", null, "directory", null);
    cfg.setBoolean("sendemail", null, "enable", false);
    cfg.setInt("cache", "projects", "checkFrequency", 0);
    cfg.setInt("plugins", null, "checkFrequency", 0);
  }

  private static String format(InetSocketAddress s) {
    return String.format("%s:%d", s.getAddress().getHostAddress(), s.getPort());
  }

  private static Injector createTestInjector(Daemon daemon) throws Exception {
    Injector sysInjector = get(daemon, "sysInjector");
    Module module = new FactoryModule() {
      @Override
      protected void configure() {
        bind(AccountCreator.class);
      }
    };
    return sysInjector.createChildInjector(module);
  }

  @SuppressWarnings("unchecked")
  private static <T> T get(Object obj, String field) throws SecurityException,
      NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
    Field f = obj.getClass().getDeclaredField(field);
    f.setAccessible(true);
    return (T) f.get(obj);
  }

  private static final InetSocketAddress newPort() throws IOException {
    ServerSocket s = new ServerSocket(0, 0, getLocalHost());
    try {
      return (InetSocketAddress) s.getLocalSocketAddress();
    } finally {
      s.close();
    }
  }

  private static InetAddress getLocalHost() throws UnknownHostException {
    try {
      return InetAddress.getLocalHost();
    } catch (UnknownHostException e1) {
      try {
        return InetAddress.getByName("localhost");
      } catch (UnknownHostException e2) {
        return InetAddress.getByName("127.0.0.1");
      }
    }
  }

  private File sitePath;
  private Daemon daemon;
  private ExecutorService daemonService;
  private Injector testInjector;
  private String url;
  private InetSocketAddress sshdAddress;
  private InetSocketAddress httpAddress;

  private GerritServer(File sitePath, Injector testInjector, Daemon daemon,
      ExecutorService daemonService) throws IOException, ConfigInvalidException {
    this.sitePath = sitePath;
    this.testInjector = testInjector;
    this.daemon = daemon;
    this.daemonService = daemonService;

    Config cfg = testInjector.getInstance(
      Key.get(Config.class, GerritServerConfig.class));
    url = cfg.getString("gerrit", null, "canonicalWebUrl");
    URI uri = URI.create(url);

    sshdAddress = SocketUtil.resolve(
        cfg.getString("sshd", null, "listenAddress"),
        0);
    httpAddress = new InetSocketAddress(uri.getHost(), uri.getPort());
  }

  String getUrl() {
    return url;
  }

  InetSocketAddress getSshdAddress() {
    return sshdAddress;
  }

  InetSocketAddress getHttpAddress() {
    return httpAddress;
  }

  Injector getTestInjector() {
    return testInjector;
  }

  void stop() throws Exception {
    daemon.getLifecycleManager().stop();
    if (daemonService != null) {
      System.out.println("Gerrit Server Shutdown");
      daemonService.shutdownNow();
      daemonService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
    }
    if (sitePath != null) {
      TempFileUtil.recursivelyDelete(sitePath);
    }
    RepositoryCache.clear();
  }
}
