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

import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.pgm.Daemon;
import com.google.gerrit.pgm.Init;
import com.google.gerrit.server.config.FactoryModule;
import com.google.gerrit.server.util.SocketUtil;
import com.google.inject.Injector;
import com.google.inject.Module;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class GerritServer {

  /** Returns fully started Gerrit server */
  static GerritServer start() throws Exception {
    final File site = initSite();
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

    ExecutorService daemonService = Executors.newSingleThreadExecutor();
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

    Injector i = createTestInjector(daemon);
    return new GerritServer(site, i, daemon, daemonService);
  }

  private static File initSite() throws Exception {
    File tmp = TempFileUtil.createTempDirectory();
    Init init = new Init();
    int rc = init.main(new String[] {
        "-d", tmp.getPath(), "--batch", "--no-auto-start"});
    if (rc != 0) {
      throw new RuntimeException("Couldn't initialize site");
    }

    InetSocketAddress http = newPort();
    InetSocketAddress sshd = newPort();
    String url = "http://" + http + "/";
    FileBasedConfig cfg = new FileBasedConfig(
        new File(new File(tmp, "etc"), "gerrit.config"),
        FS.DETECTED);
    cfg.load();
    cfg.setString("gerrit", null, "canonicalWebUrl", url);
    cfg.setString("httpd", null, "listenUrl", url);
    cfg.setString("sshd", null, "listenAddress", SocketUtil.format(sshd, 0));
    cfg.save();
    return tmp;
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
    ServerSocket s = new ServerSocket(0, 0, InetAddress.getLocalHost());
    try {
      return (InetSocketAddress) s.getLocalSocketAddress();
    } finally {
      s.close();
    }
  }

  private File sitePath;
  private Daemon daemon;
  private ExecutorService daemonService;
  private Injector testInjector;
  private String url;
  private int sshdPort;
  private int httpPort;

  private GerritServer(File sitePath, Injector testInjector, Daemon daemon,
      ExecutorService daemonService) throws IOException, ConfigInvalidException {
    this.sitePath = sitePath;
    this.testInjector = testInjector;
    this.daemon = daemon;
    this.daemonService = daemonService;

    FileBasedConfig cfg = new FileBasedConfig(
        new File(new File(sitePath, "etc"), "gerrit.config"),
        FS.DETECTED);
    cfg.load();
    url = cfg.getString("gerrit", null, "canonicalWebUrl");
    sshdPort = SocketUtil.parse(
        cfg.getString("sshd", null, "listenAddress"),
        0).getPort();
    httpPort = URI.create(url).getPort();
  }

  String getUrl() {
    return url;
  }

  int getSshdPort() {
    return sshdPort;
  }

  int getHttpPort() {
    return httpPort;
  }

  Injector getTestInjector() {
    return testInjector;
  }

  void stop() throws Exception {
    LifecycleManager manager = get(daemon, "manager");
    System.out.println("Gerrit Server Shutdown");
    manager.stop();
    daemonService.shutdownNow();
    daemonService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
    TempFileUtil.recursivelyDelete(sitePath);
  }
}
